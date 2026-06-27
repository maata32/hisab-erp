package com.hisaberp.phase1b;

import com.hisaberp.HisabErpApplication;
import com.hisaberp.sales.api.SalesDto;
import com.hisaberp.sales.internal.SalesService;
import com.hisaberp.shared.error.BusinessException;
import com.hisaberp.shared.tenant.TenantContext;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Avoirs are total-only: one credit note clears the entire invoice. Happy path
 * checks the full credit lands on balance; guards cover (1) cancelled invoice
 * (2) draft invoice (3) double-credit on the same invoice.
 */
@SpringBootTest(classes = HisabErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Credit notes — total avoir + invariant guards")
class CreditNoteIT {

    @Autowired
    SalesService salesService;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager em;

    UUID tenantId;
    UUID customerId;
    UUID productAId;
    UUID productBId;
    UUID uomId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'CreditNote Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "cntest-" + tenantId);

        TenantContext.set(tenantId);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());

        UUID uomCatId = UUID.randomUUID();
        uomId = UUID.randomUUID();
        productAId = UUID.randomUUID();
        productBId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        jdbc.update("INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version) VALUES (?,?,?,?,now(),now(),0)",
                uomCatId, tenantId, "COUNT-CN", "Count");
        jdbc.update("INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base, decimal_places, created_at, updated_at, version) VALUES (?,?,?,?,?,1,true,0,now(),now(),0)",
                uomId, tenantId, uomCatId, "PCE-CN", "Piece");
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, tracks_serial, is_sellable, is_purchasable,
                                      is_active, created_at, updated_at, version)
                VALUES (?,?,?,?,?,0.00,false,false,true,true,true,now(),now(),0)
                """, productAId, tenantId, "SKU-CN-A", "Product A", uomId);
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, tracks_serial, is_sellable, is_purchasable,
                                      is_active, created_at, updated_at, version)
                VALUES (?,?,?,?,?,0.00,false,false,true,true,true,now(),now(),0)
                """, productBId, tenantId, "SKU-CN-B", "Product B", uomId);
        // Variant = SKU: seed each product's default variant reusing the product id so
        // sales lines (SalesService resolves the variant) resolve to a real variant.
        jdbc.update("INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default, is_active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,true,now(),now(),0)", productAId, tenantId, productAId, "SKU-CN-A");
        jdbc.update("INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default, is_active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,true,now(),now(),0)", productBId, tenantId, productBId, "SKU-CN-B");
        jdbc.update("INSERT INTO parties (id, tenant_id, code, name, is_customer, is_supplier, active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,false,true,now(),now(),0)",
                customerId, tenantId, "C-CNTEST-" + customerId, "CreditNote Test Customer");
    }

    @Test
    void totalAvoirClearsBalanceAndMarksApplied() {
        SalesDto.InvoiceDto inv = createInvoice(List.of(
                line(productAId, new BigDecimal("10"), new BigDecimal("100")),  // 1000
                line(productBId, new BigDecimal("5"),  new BigDecimal("50"))    //  250
        ));
        assertThat(inv.total()).isEqualByComparingTo("1250.00");
        assertThat(inv.balance()).isEqualByComparingTo("1250.00");

        SalesDto.CreditNoteDto cn = salesService.createCreditNote(inv.id(),
                new SalesDto.CreateCreditNoteRequest("Return"));

        assertThat(cn.status()).isEqualTo("APPLIED");
        assertThat(cn.subtotal()).isEqualByComparingTo("1250.00");
        assertThat(cn.total()).isEqualByComparingTo("1250.00");
        assertThat(cn.lines()).hasSize(2);

        SalesDto.InvoiceDto refreshed = salesService.getInvoice(inv.id());
        assertThat(refreshed.balance()).isEqualByComparingTo("0.00");
        // A total avoir letters the invoice: the credit note settles it, so the
        // invoice reads PAID (balance 0) — no more REFUNDED status.
        assertThat(refreshed.status()).isEqualTo("PAID");
    }

    @Nested
    @DisplayName("Guards")
    class Guards {

        @Test
        void rejectsCreditOnCancelledInvoice() {
            SalesDto.InvoiceDto inv = createInvoice(List.of(
                    line(productAId, new BigDecimal("2"), new BigDecimal("50"))
            ));
            // Force CANCELLED directly — there is no service method to cancel anymore.
            jdbc.update("UPDATE invoices SET status = 'CANCELLED' WHERE id = ?", inv.id());

            assertThatThrownBy(() -> salesService.createCreditNote(inv.id(),
                    new SalesDto.CreateCreditNoteRequest("Try")))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getMessageKey()).isEqualTo("error.creditnote.invoice_cancelled"));
        }

        @Test
        void rejectsCreditOnDraftInvoice() {
            SalesDto.InvoiceDto inv = createInvoice(List.of(
                    line(productAId, new BigDecimal("5"), new BigDecimal("20"))
            ));
            // Force DRAFT — DRAFT invoices are not creditable.
            jdbc.update("UPDATE invoices SET status = 'DRAFT' WHERE id = ?", inv.id());

            assertThatThrownBy(() -> salesService.createCreditNote(inv.id(),
                    new SalesDto.CreateCreditNoteRequest("Too early")))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getMessageKey()).isEqualTo("error.creditnote.invoice_draft"));
        }

        @Test
        void rejectsSecondCreditOnAlreadyCreditedInvoice() {
            SalesDto.InvoiceDto inv = createInvoice(List.of(
                    line(productAId, new BigDecimal("4"), new BigDecimal("25"))
            ));

            // First credit clears everything.
            salesService.createCreditNote(inv.id(),
                    new SalesDto.CreateCreditNoteRequest("Full"));

            // The invoice is now settled by the avoir (PAID, one avoir per
            // invoice), so the second attempt is rejected by the already-credited
            // count check → error.creditnote.already_credited.
            assertThatThrownBy(() -> salesService.createCreditNote(inv.id(),
                    new SalesDto.CreateCreditNoteRequest("Again")))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getMessageKey()).isEqualTo("error.creditnote.already_credited"));
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private SalesDto.LineRequest line(UUID productId, BigDecimal qty, BigDecimal unitPrice) {
        return new SalesDto.LineRequest(productId, uomId, qty, unitPrice, BigDecimal.ZERO);
    }

    private SalesDto.InvoiceDto createInvoice(List<SalesDto.LineRequest> lines) {
        return salesService.createInvoice(new SalesDto.CreateInvoiceRequest(
                customerId, null, LocalDate.now(), LocalDate.now().plusDays(30),
                null, "MRU", null, lines));
    }
}
