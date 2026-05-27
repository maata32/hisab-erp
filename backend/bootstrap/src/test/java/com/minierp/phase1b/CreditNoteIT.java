package com.minierp.phase1b;

import com.minierp.MiniErpApplication;
import com.minierp.sales.api.SalesDto;
import com.minierp.sales.internal.SalesService;
import com.minierp.shared.error.BusinessException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Credit notes are line-based and applied to the source invoice on creation.
 * Happy path covers two-line credit; three guards cover the most common
 * rejection paths (cancelled invoice, over-credit, double-credit).
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Credit notes — happy path + invariant guards")
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
        jdbc.update("INSERT INTO parties (id, tenant_id, code, name, is_customer, is_supplier, active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,false,true,now(),now(),0)",
                customerId, tenantId, "C-CNTEST-" + customerId, "CreditNote Test Customer");
    }

    @Test
    void creditTwoLinesAppliesToBalanceAndMarksApplied() {
        SalesDto.InvoiceDto inv = createInvoice(List.of(
                line(productAId, new BigDecimal("10"), new BigDecimal("100")),  // 1000
                line(productBId, new BigDecimal("5"),  new BigDecimal("50"))    //  250
        ));
        assertThat(inv.total()).isEqualByComparingTo("1250.00");
        assertThat(inv.balance()).isEqualByComparingTo("1250.00");

        UUID lineA = inv.lines().stream().filter(l -> l.productId().equals(productAId)).findFirst().orElseThrow().id();
        UUID lineB = inv.lines().stream().filter(l -> l.productId().equals(productBId)).findFirst().orElseThrow().id();

        SalesDto.CreditNoteDto cn = salesService.createCreditNote(inv.id(),
                new SalesDto.CreateCreditNoteRequest("Return", List.of(
                        new SalesDto.CreateCreditNoteLine(lineA, new BigDecimal("3")),  // 300
                        new SalesDto.CreateCreditNoteLine(lineB, new BigDecimal("2"))   // 100
                )));

        assertThat(cn.status()).isEqualTo("APPLIED");
        assertThat(cn.subtotal()).isEqualByComparingTo("400.00");
        assertThat(cn.total()).isEqualByComparingTo("400.00");
        assertThat(cn.lines()).hasSize(2);

        SalesDto.InvoiceDto refreshed = salesService.getInvoice(inv.id());
        assertThat(refreshed.balance()).isEqualByComparingTo("850.00");
        assertThat(refreshed.status()).isEqualTo("PARTIAL");
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

            UUID lineId = inv.lines().get(0).id();
            assertThatThrownBy(() -> salesService.createCreditNote(inv.id(),
                    new SalesDto.CreateCreditNoteRequest("Try", List.of(
                            new SalesDto.CreateCreditNoteLine(lineId, BigDecimal.ONE)))))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getMessageKey()).isEqualTo("error.creditnote.invoice_cancelled"));
        }

        @Test
        void rejectsLineQuantityExceedingInvoiced() {
            SalesDto.InvoiceDto inv = createInvoice(List.of(
                    line(productAId, new BigDecimal("5"), new BigDecimal("20"))
            ));
            UUID lineId = inv.lines().get(0).id();

            assertThatThrownBy(() -> salesService.createCreditNote(inv.id(),
                    new SalesDto.CreateCreditNoteRequest("Over", List.of(
                            new SalesDto.CreateCreditNoteLine(lineId, new BigDecimal("10"))))))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getMessageKey()).isEqualTo("error.creditnote.line_exceeds_invoiced"));
        }

        @Test
        void rejectsSecondCreditOnFullyCreditedInvoice() {
            SalesDto.InvoiceDto inv = createInvoice(List.of(
                    line(productAId, new BigDecimal("4"), new BigDecimal("25"))
            ));
            UUID lineId = inv.lines().get(0).id();

            // First credit covers the entire line.
            salesService.createCreditNote(inv.id(),
                    new SalesDto.CreateCreditNoteRequest("Full", List.of(
                            new SalesDto.CreateCreditNoteLine(lineId, new BigDecimal("4")))));

            assertThatThrownBy(() -> salesService.createCreditNote(inv.id(),
                    new SalesDto.CreateCreditNoteRequest("Again", List.of(
                            new SalesDto.CreateCreditNoteLine(lineId, BigDecimal.ONE)))))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getMessageKey()).isEqualTo("error.creditnote.invoice_fully_credited"));
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
