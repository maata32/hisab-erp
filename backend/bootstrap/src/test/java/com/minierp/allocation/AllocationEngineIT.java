package com.minierp.allocation;

import com.minierp.MiniErpApplication;
import com.minierp.allocation.api.AllocationEngine;
import com.minierp.allocation.api.AllocationProposal;
import com.minierp.allocation.api.OpenItem;
import com.minierp.payment.api.PaymentDto;
import com.minierp.payment.internal.PaymentService;
import com.minierp.purchase.api.PurchaseDto;
import com.minierp.purchase.internal.PurchaseService;
import com.minierp.sales.api.SalesDto;
import com.minierp.sales.internal.SalesService;
import com.minierp.shared.tenant.TenantContext;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 read-only contract of {@link AllocationEngine}: open-items assembly
 * across modules + FIFO proposal. Writes (apply / unapply) come in Phase 2 —
 * the engine here does not mutate any source row.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("AllocationEngine — open items + FIFO propose")
class AllocationEngineIT {

    @Autowired AllocationEngine engine;
    @Autowired PaymentService paymentService;
    @Autowired SalesService salesService;
    @Autowired PurchaseService purchaseService;
    @Autowired JdbcTemplate jdbc;

    @PersistenceContext EntityManager em;

    UUID tenantId;
    UUID customerId;
    UUID productId;
    UUID uomId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Allocation Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "alloctest-" + tenantId);

        TenantContext.set(tenantId);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);

        customerId = UUID.randomUUID();
        uomId = UUID.randomUUID();
        productId = UUID.randomUUID();
        UUID uomCatId = UUID.randomUUID();

        jdbc.update("INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version) VALUES (?,?,?,?,now(),now(),0)",
                uomCatId, tenantId, "COUNT-ALLOC", "Count");
        jdbc.update("INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base, decimal_places, created_at, updated_at, version) VALUES (?,?,?,?,?,1,true,0,now(),now(),0)",
                uomId, tenantId, uomCatId, "PCE-ALLOC", "Piece");
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, tracks_serial, is_sellable, is_purchasable,
                                      is_active, created_at, updated_at, version)
                VALUES (?,?,?,?,?,0.00,false,false,true,true,true,now(),now(),0)
                """, productId, tenantId, "SKU-ALLOCTEST", "Allocation Test Product", uomId);
        jdbc.update("INSERT INTO parties (id, tenant_id, code, name, is_customer, is_supplier, active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,false,true,now(),now(),0)",
                customerId, tenantId, "C-ALLOCTEST-" + customerId, "Allocation Test Customer");
    }

    @Nested
    @DisplayName("findOpenItemsByParty")
    class FindOpenItems {

        @Test
        @DisplayName("returns unpaid invoices as NEGATIVE items")
        void invoicesAreNegative() {
            UUID inv1 = createInvoice(new BigDecimal("100.00"), LocalDate.now().minusDays(10));
            UUID inv2 = createInvoice(new BigDecimal("200.00"), LocalDate.now().minusDays(5));

            List<OpenItem> items = engine.findOpenItemsByParty(customerId);
            Map<UUID, OpenItem> byId = items.stream()
                    .collect(Collectors.toMap(OpenItem::sourceId, i -> i));

            assertThat(byId).containsKeys(inv1, inv2);
            assertThat(byId.get(inv1).sign()).isEqualTo(OpenItem.Sign.NEGATIVE);
            assertThat(byId.get(inv1).sourceType()).isEqualTo("INVOICE");
            assertThat(byId.get(inv1).amountOpen()).isEqualByComparingTo("100.00");
            assertThat(byId.get(inv2).amountOpen()).isEqualByComparingTo("200.00");
        }

        @Test
        @DisplayName("payment surplus → CUSTOMER_CREDIT surfaces as POSITIVE item")
        void surplusCreditIsPositive() {
            // 100 invoice + 500 payment with explicit surplus CUSTOMER_CREDIT
            // allocation (mimics the front-end "Nouveau paiement" save path).
            UUID inv = createInvoice(new BigDecimal("100.00"), LocalDate.now());
            PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "CUSTOMER_PAYMENT", customerId, new BigDecimal("500.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "Overpayment test",
                    List.of(
                            new PaymentDto.AllocationRequest("SALE_INVOICE", inv,
                                    new BigDecimal("100.00"), null),
                            new PaymentDto.AllocationRequest("CUSTOMER_CREDIT", customerId,
                                    new BigDecimal("400.00"), null))));
            paymentService.confirm(pay.id(), null);

            List<OpenItem> items = engine.findOpenItemsByParty(customerId);
            OpenItem credit = items.stream()
                    .filter(i -> "CUSTOMER_CREDIT".equals(i.sourceType()))
                    .findFirst()
                    .orElseThrow();

            assertThat(credit.sign()).isEqualTo(OpenItem.Sign.POSITIVE);
            assertThat(credit.amountOpen()).isEqualByComparingTo("400.00");

            // #4 regression guard: the payment itself must be FULLY consumed
            // (100 invoice + 400 surplus credit = 500), i.e. NOT surface as an
            // open PAYMENT item. A naive GREATEST-removal that summed only the
            // invoice allocations (100) would wrongly leave 400 open here AND
            // also count the 400 credit — double-counting the surplus.
            assertThat(items.stream().filter(i -> "PAYMENT".equals(i.sourceType())))
                    .as("payment with surplus→credit has no residual")
                    .isEmpty();
        }

        @Test
        @DisplayName("Phase 5: confirming a payment double-writes invoice allocations")
        void doubleWriteOnConfirm() {
            UUID inv = createInvoice(new BigDecimal("100.00"), LocalDate.now());

            PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "CUSTOMER_PAYMENT", customerId, new BigDecimal("100.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "Double-write test",
                    List.of(new PaymentDto.AllocationRequest("SALE_INVOICE", inv,
                            new BigDecimal("100.00"), null))));

            // Before confirm: no engine row.
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allocations WHERE positive_id = ?",
                    Long.class, pay.id())).isZero();

            paymentService.confirm(pay.id(), null);

            // After confirm: one engine row pointing PAYMENT → INVOICE.
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allocations WHERE positive_type = 'PAYMENT' " +
                    "AND positive_id = ? AND negative_type = 'INVOICE' AND negative_id = ?",
                    Long.class, pay.id(), inv)).isEqualTo(1L);

            // Legacy payment_allocations row also exists (double-write, not replace).
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM payment_allocations WHERE payment_id = ?",
                    Long.class, pay.id())).isEqualTo(1L);
        }

        @Test
        @DisplayName("#2: allocation history returns labeled rows on both sides")
        void allocationHistoryWithLabels() {
            UUID inv = createInvoice(new BigDecimal("100.00"), LocalDate.now());
            PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "CUSTOMER_PAYMENT", customerId, new BigDecimal("100.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "History test",
                    List.of(new PaymentDto.AllocationRequest("SALE_INVOICE", inv,
                            new BigDecimal("100.00"), null))));
            paymentService.confirm(pay.id(), null);

            // One active history row, labeled on both sides (payment number ↔ invoice number).
            var rows = engine.findAllocationHistoryByParty(customerId);
            assertThat(rows).hasSize(1);
            var row = rows.get(0);
            assertThat(row.positiveType()).isEqualTo("PAYMENT");
            assertThat(row.negativeType()).isEqualTo("INVOICE");
            assertThat(row.positiveLabel()).isEqualTo(pay.number());
            assertThat(row.negativeLabel()).isNotBlank();
            assertThat(row.amount()).isEqualByComparingTo("100.00");
            assertThat(row.reversedAt()).isNull();
        }

        @Test
        @DisplayName("Phase 6: creating an avoir on an unpaid invoice writes CREDIT_NOTE → INVOICE allocation")
        void creditNoteToInvoiceMirrored() {
            UUID inv = createInvoice(new BigDecimal("250.00"), LocalDate.now());
            // Total avoir on the invoice — applyCredit will impute the full 250.
            var cn = salesService.createCreditNote(inv,
                    new SalesDto.CreateCreditNoteRequest("test avoir"));

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allocations " +
                    "WHERE positive_type = 'CREDIT_NOTE' AND positive_id = ? " +
                    "  AND negative_type = 'INVOICE' AND negative_id = ?",
                    Long.class, cn.id(), inv)).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT amount FROM allocations " +
                    "WHERE positive_type = 'CREDIT_NOTE' AND positive_id = ?",
                    BigDecimal.class, cn.id())).isEqualByComparingTo("250.00");
        }

        @Test
        @DisplayName("Phase 6: avoir on a fully-prepaid invoice writes NO allocation (full surplus)")
        void creditNoteFullSurplusSkipped() {
            // 100 invoice, paid in full → balance 0. Avoir would route entirely
            // to surplus credit, imputed=0 → no allocation row to write.
            UUID inv = createInvoice(new BigDecimal("100.00"), LocalDate.now());
            PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "CUSTOMER_PAYMENT", customerId, new BigDecimal("100.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "Prepay",
                    List.of(new PaymentDto.AllocationRequest("SALE_INVOICE", inv,
                            new BigDecimal("100.00"), null))));
            paymentService.confirm(pay.id(), null);

            var cn = salesService.createCreditNote(inv,
                    new SalesDto.CreateCreditNoteRequest("test avoir on prepaid"));

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allocations WHERE positive_type = 'CREDIT_NOTE' AND positive_id = ?",
                    Long.class, cn.id())).isZero();
        }

        @Test
        @DisplayName("Phase 5: surplus → CUSTOMER_CREDIT allocation row is NOT mirrored")
        void surplusCreditNotMirrored() {
            // Surplus rows live on a different conceptual axis — they mint a
            // credit, not pair two open items. The listener must skip them.
            UUID inv = createInvoice(new BigDecimal("100.00"), LocalDate.now());
            PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "CUSTOMER_PAYMENT", customerId, new BigDecimal("500.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "Surplus test",
                    List.of(
                            new PaymentDto.AllocationRequest("SALE_INVOICE", inv,
                                    new BigDecimal("100.00"), null),
                            new PaymentDto.AllocationRequest("CUSTOMER_CREDIT", customerId,
                                    new BigDecimal("400.00"), null))));
            paymentService.confirm(pay.id(), null);

            // INVOICE allocation got mirrored.
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allocations WHERE positive_id = ? AND negative_type = 'INVOICE'",
                    Long.class, pay.id())).isEqualTo(1L);
            // CUSTOMER_CREDIT target did NOT.
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allocations WHERE positive_id = ? AND negative_type = 'CUSTOMER_CREDIT'",
                    Long.class, pay.id())).isZero();
        }

        @Test
        @DisplayName("supplier side: purchase invoice NEGATIVE + unallocated payment POSITIVE")
        void supplierSide() {
            UUID supplierId = UUID.randomUUID();
            jdbc.update("INSERT INTO parties (id, tenant_id, code, name, is_customer, is_supplier, active, created_at, updated_at, version) " +
                    "VALUES (?,?,?,?,false,true,true,now(),now(),0)",
                    supplierId, tenantId, "S-ALLOC-" + supplierId, "Allocation Test Supplier");

            // Purchase invoice for 1000, will sit on NEGATIVE side.
            PurchaseDto.PurchaseInvoiceDto pinv = purchaseService.createInvoice(
                    new PurchaseDto.CreatePurchaseInvoiceRequest(
                            supplierId, null, "SUP-ALLOC-1",
                            LocalDate.now(), LocalDate.now().plusDays(30),
                            "MRU", "Allocation test",
                            List.of(new PurchaseDto.LineRequest(productId, uomId,
                                    BigDecimal.ONE, new BigDecimal("1000.00"), BigDecimal.ZERO))));

            // Supplier payment of 1500 covering only 1000 of the invoice → 500 residual.
            PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "SUPPLIER_PAYMENT", supplierId, new BigDecimal("1500.00"), "MRU",
                    LocalDate.now(), "BANK_TRANSFER", "VIR-ALLOC", "BANK-1",
                    "With surplus",
                    List.of(new PaymentDto.AllocationRequest("PURCHASE_INVOICE", pinv.id(),
                            new BigDecimal("1000.00"), null))));
            paymentService.confirm(pay.id(), null);

            List<OpenItem> items = engine.findOpenItemsByParty(supplierId);
            OpenItem payment = items.stream()
                    .filter(i -> "SUPPLIER_PAYMENT".equals(i.sourceType()))
                    .findFirst()
                    .orElseThrow();

            assertThat(payment.sign()).isEqualTo(OpenItem.Sign.POSITIVE);
            assertThat(payment.amountOpen()).isEqualByComparingTo("500.00");
            // The purchase invoice was fully paid (1000 of 1000) → no longer open.
            assertThat(items.stream().map(OpenItem::sourceType)).doesNotContain("PURCHASE_INVOICE");
        }

        @Test
        @DisplayName("#3: a SUPPLIER_REFUND created without allocation surfaces as a POSITIVE open item")
        void supplierRefundIsPositiveOpenItem() {
            // The new UI lets a supplier-side "Versement" create a SUPPLIER_REFUND
            // with no allocation: money received from a supplier becomes a free
            // positive item the engine can re-impute later (e.g. against a future
            // retrait, or against a sale invoice if the party is also a customer).
            UUID supplierId = UUID.randomUUID();
            jdbc.update("INSERT INTO parties (id, tenant_id, code, name, is_customer, is_supplier, active, created_at, updated_at, version) " +
                    "VALUES (?,?,?,?,false,true,true,now(),now(),0)",
                    supplierId, tenantId, "S-REFUND-" + supplierId, "Supplier Refund Test");

            PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "SUPPLIER_REFUND", supplierId, new BigDecimal("750.00"), "MRU",
                    LocalDate.now(), "BANK_TRANSFER", null, null, "Refund from supplier", null));
            paymentService.confirm(pay.id(), null);

            OpenItem refund = engine.findOpenItemsByParty(supplierId).stream()
                    .filter(i -> "SUPPLIER_PAYMENT".equals(i.sourceType()) && pay.id().equals(i.sourceId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(refund.sign()).isEqualTo(OpenItem.Sign.POSITIVE);
            assertThat(refund.amountOpen()).isEqualByComparingTo("750.00");
        }

        @Test
        @DisplayName("supplier versement imputed on a retrait: consumes the versement residual, writes audit row, retrait cash unchanged")
        void applySupplierRefundToRetrait() {
            UUID supplierId = UUID.randomUUID();
            jdbc.update("INSERT INTO parties (id, tenant_id, code, name, is_customer, is_supplier, active, created_at, updated_at, version) " +
                    "VALUES (?,?,?,?,false,true,true,now(),now(),0)",
                    supplierId, tenantId, "S-IMPUTE-" + supplierId, "Supplier Impute Test");

            // Versement: the supplier gave us back 750 (open positive item).
            PaymentDto.PaymentResponse versement = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "SUPPLIER_REFUND", supplierId, new BigDecimal("750.00"), "MRU",
                    LocalDate.now(), "BANK_TRANSFER", null, null, "Versement fournisseur", null));
            paymentService.confirm(versement.id(), null);

            // Retrait: we pay the supplier 1000 (cash out).
            PaymentDto.PaymentResponse retrait = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "SUPPLIER_PAYMENT", supplierId, new BigDecimal("1000.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "Retrait fournisseur", null));
            paymentService.confirm(retrait.id(), null);

            // Impute 400 of the 750 versement onto the retrait.
            BigDecimal consumed = engine.applySupplierRefundToRetrait(
                    versement.id(), retrait.id(), new BigDecimal("400.00"));
            assertThat(consumed).isEqualByComparingTo("400.00");

            // Audit row: versement (positive) → retrait (negative), amount 400.
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allocations " +
                    "WHERE positive_type = 'SUPPLIER_PAYMENT' AND positive_id = ? " +
                    "  AND negative_type = 'SUPPLIER_PAYMENT' AND negative_id = ?",
                    Long.class, versement.id(), retrait.id())).isEqualTo(1L);

            // Versement residual consumed: 750 → 350 (still open, reusable).
            OpenItem versementOpen = engine.findOpenItemsByParty(supplierId).stream()
                    .filter(i -> "SUPPLIER_PAYMENT".equals(i.sourceType()) && versement.id().equals(i.sourceId()))
                    .findFirst().orElseThrow();
            assertThat(versementOpen.amountOpen()).isEqualByComparingTo("350.00");

            // Retrait is on the NEGATIVE side → its cash residual is unchanged (1000).
            OpenItem retraitOpen = engine.findOpenItemsByParty(supplierId).stream()
                    .filter(i -> "SUPPLIER_PAYMENT".equals(i.sourceType()) && retrait.id().equals(i.sourceId()))
                    .findFirst().orElseThrow();
            assertThat(retraitOpen.amountOpen()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("skips invoices in DRAFT / CANCELLED / PAID / REFUNDED")
        void terminalStatesExcluded() {
            // PAID invoice via createInvoice + applyPayment → balance=0 → excluded.
            UUID paid = createInvoice(new BigDecimal("100.00"), LocalDate.now().minusDays(1));
            PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "CUSTOMER_PAYMENT", customerId, new BigDecimal("100.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "Pay in full", null));
            paymentService.autoAllocate(new PaymentDto.AutoAllocateRequest(customerId, pay.id(), null));
            paymentService.confirm(pay.id(), null);

            List<OpenItem> items = engine.findOpenItemsByParty(customerId);
            assertThat(items.stream().map(OpenItem::sourceId)).doesNotContain(paid);
        }
    }

    @Nested
    @DisplayName("propose")
    class Propose {

        @Test
        @DisplayName("returns empty when source amount is zero")
        void emptyOnZero() {
            UUID inv = createInvoice(new BigDecimal("100.00"), LocalDate.now());
            AllocationProposal p = engine.propose(customerId, "INVOICE", inv, BigDecimal.ZERO);
            assertThat(p.lines()).isEmpty();
            assertThat(p.surplus()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("returns surplus when no opposite item exists")
        void noOppositeSide() {
            UUID inv = createInvoice(new BigDecimal("100.00"), LocalDate.now());
            // INVOICE is NEGATIVE; opposite = POSITIVE. No payment / credit exists.
            AllocationProposal p = engine.propose(customerId, "INVOICE", inv, new BigDecimal("100.00"));
            assertThat(p.lines()).isEmpty();
            assertThat(p.surplus()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    @DisplayName("applyCreditToInvoice")
    class ApplyCreditToInvoice {

        @Test
        @DisplayName("reduces invoice balance + credit remaining + writes allocation row")
        void applyEndToEnd() {
            // Seed: 100 invoice + 500 payment with explicit 400 surplus → CUSTOMER_CREDIT of 400.
            UUID firstInv = createInvoice(new BigDecimal("100.00"), LocalDate.now());
            PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "CUSTOMER_PAYMENT", customerId, new BigDecimal("500.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "Seed credit",
                    List.of(
                            new PaymentDto.AllocationRequest("SALE_INVOICE", firstInv,
                                    new BigDecimal("100.00"), null),
                            new PaymentDto.AllocationRequest("CUSTOMER_CREDIT", customerId,
                                    new BigDecimal("400.00"), null))));
            paymentService.confirm(pay.id(), null);

            UUID creditId = jdbc.queryForObject(
                    "SELECT id FROM customer_credits WHERE party_id = ? AND status = 'ACTIVE'",
                    UUID.class, customerId);

            // New 250 invoice — apply 250 from the 400 credit.
            UUID newInv = createInvoice(new BigDecimal("250.00"), LocalDate.now());
            BigDecimal applied = engine.applyCreditToInvoice(creditId, newInv, new BigDecimal("250.00"));

            assertThat(applied).isEqualByComparingTo("250.00");

            // Invoice now PAID, balance 0.
            assertThat(jdbc.queryForObject(
                    "SELECT balance FROM invoices WHERE id = ?", BigDecimal.class, newInv))
                    .isEqualByComparingTo("0.00");
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM invoices WHERE id = ?", String.class, newInv))
                    .isEqualTo("PAID");

            // Credit reduced by 250 (400 → 150) and still ACTIVE.
            assertThat(jdbc.queryForObject(
                    "SELECT remaining_amount FROM customer_credits WHERE id = ?",
                    BigDecimal.class, creditId)).isEqualByComparingTo("150.00");
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM customer_credits WHERE id = ?",
                    String.class, creditId)).isEqualTo("ACTIVE");

            // One audit row in allocations.
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allocations " +
                    "WHERE positive_type = 'CUSTOMER_CREDIT' AND positive_id = ? " +
                    "  AND negative_type = 'INVOICE' AND negative_id = ?",
                    Long.class, creditId, newInv);
            assertThat(count).isEqualTo(1L);
        }

        @Test
        @DisplayName("refund payment + credit → settles credit, audit row written")
        void applyCreditToRefund() {
            // Seed: 100 invoice + 500 payment → 400 OVERPAYMENT credit.
            UUID firstInv = createInvoice(new BigDecimal("100.00"), LocalDate.now());
            PaymentDto.PaymentResponse seed = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "CUSTOMER_PAYMENT", customerId, new BigDecimal("500.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "Seed credit",
                    List.of(
                            new PaymentDto.AllocationRequest("SALE_INVOICE", firstInv,
                                    new BigDecimal("100.00"), null),
                            new PaymentDto.AllocationRequest("CUSTOMER_CREDIT", customerId,
                                    new BigDecimal("400.00"), null))));
            paymentService.confirm(seed.id(), null);
            UUID creditId = jdbc.queryForObject(
                    "SELECT id FROM customer_credits WHERE party_id = ? AND status = 'ACTIVE'",
                    UUID.class, customerId);

            // Operator pays the customer back 400 via a CUSTOMER_REFUND payment.
            PaymentDto.PaymentResponse refund = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "CUSTOMER_REFUND", customerId, new BigDecimal("400.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "Customer cash refund", null));
            paymentService.confirm(refund.id(), null);

            BigDecimal applied = engine.applyCreditToRefund(creditId, refund.id(), new BigDecimal("400.00"));
            assertThat(applied).isEqualByComparingTo("400.00");

            // Credit fully consumed → EXHAUSTED, remaining 0.
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM customer_credits WHERE id = ?", String.class, creditId))
                    .isEqualTo("EXHAUSTED");
            assertThat(jdbc.queryForObject(
                    "SELECT remaining_amount FROM customer_credits WHERE id = ?",
                    BigDecimal.class, creditId)).isEqualByComparingTo("0.00");

            // One audit row pointing CREDIT → REFUND payment.
            Long count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM allocations " +
                    "WHERE positive_type = 'CUSTOMER_CREDIT' AND positive_id = ? " +
                    "  AND negative_type = 'PAYMENT' AND negative_id = ?",
                    Long.class, creditId, refund.id());
            assertThat(count).isEqualTo(1L);
        }

        @Test
        @DisplayName("caps to min(invoice.balance, credit.remaining, amount)")
        void capsToOpenBalances() {
            // 100 invoice + 50 OVERPAYMENT credit. Ask for 999 → only 50 applied.
            UUID prevInv = createInvoice(new BigDecimal("100.00"), LocalDate.now());
            PaymentDto.PaymentResponse pay = paymentService.create(new PaymentDto.CreatePaymentRequest(
                    "CUSTOMER_PAYMENT", customerId, new BigDecimal("150.00"), "MRU",
                    LocalDate.now(), "CASH", null, null, "Seed small credit",
                    List.of(
                            new PaymentDto.AllocationRequest("SALE_INVOICE", prevInv,
                                    new BigDecimal("100.00"), null),
                            new PaymentDto.AllocationRequest("CUSTOMER_CREDIT", customerId,
                                    new BigDecimal("50.00"), null))));
            paymentService.confirm(pay.id(), null);
            UUID creditId = jdbc.queryForObject(
                    "SELECT id FROM customer_credits WHERE party_id = ? AND status = 'ACTIVE'",
                    UUID.class, customerId);

            UUID inv = createInvoice(new BigDecimal("200.00"), LocalDate.now());
            BigDecimal applied = engine.applyCreditToInvoice(creditId, inv, new BigDecimal("999.00"));

            assertThat(applied).isEqualByComparingTo("50.00");
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM customer_credits WHERE id = ?",
                    String.class, creditId)).isEqualTo("EXHAUSTED");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID createInvoice(BigDecimal total, LocalDate dueDate) {
        SalesDto.CreateInvoiceRequest req = new SalesDto.CreateInvoiceRequest(
                customerId, null, LocalDate.now(), dueDate, null, "MRU", null,
                List.of(new SalesDto.LineRequest(productId, uomId, BigDecimal.ONE, total, BigDecimal.ZERO))
        );
        return salesService.createInvoice(req).id();
    }
}
