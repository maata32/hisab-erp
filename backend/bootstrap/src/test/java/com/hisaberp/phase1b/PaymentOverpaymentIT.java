package com.hisaberp.phase1b;

import com.hisaberp.HisabErpApplication;
import com.hisaberp.payment.api.PaymentDto;
import com.hisaberp.payment.internal.PaymentService;
import com.hisaberp.sales.api.SalesDto;
import com.hisaberp.sales.internal.SalesService;
import com.hisaberp.shared.tenant.TenantContext;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

/**
 * A surplus on a customer payment is recorded as an explicit allocation of
 * type CUSTOMER_CREDIT, which on apply grants an OVERPAYMENT credit. The UI
 * surfaces the surplus row to the user before save, so the destination of
 * the unallocated cash is never silent.
 */
@SpringBootTest(classes = HisabErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Payment overpayment — surplus becomes a customer credit")
class PaymentOverpaymentIT {

    @Autowired PaymentService paymentService;
    @Autowired SalesService salesService;
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
                VALUES (?, ?, 'Overpayment Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "ovp-" + tenantId);
        TenantContext.set(tenantId);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);

        customerId = UUID.randomUUID();
        uomId = UUID.randomUUID();
        productId = UUID.randomUUID();
        UUID uomCatId = UUID.randomUUID();

        jdbc.update("INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version) VALUES (?,?,?,?,now(),now(),0)",
                uomCatId, tenantId, "COUNT-OVP", "Count");
        jdbc.update("INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base, decimal_places, created_at, updated_at, version) VALUES (?,?,?,?,?,1,true,0,now(),now(),0)",
                uomId, tenantId, uomCatId, "PCE-OVP", "Piece");
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, tracks_serial, is_sellable, is_purchasable,
                                      is_active, created_at, updated_at, version)
                VALUES (?,?,?,?,?,0.00,false,false,true,true,true,now(),now(),0)
                """, productId, tenantId, "SKU-OVP", "Overpayment Test Product", uomId);
        // Variant = SKU: default variant reuses the product id (sales lines resolve the variant).
        jdbc.update("INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default, is_active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,true,now(),now(),0)", productId, tenantId, productId, "SKU-OVP");
        jdbc.update("INSERT INTO parties (id, tenant_id, code, name, is_customer, is_supplier, active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,false,true,now(),now(),0)",
                customerId, tenantId, "C-OVP-" + customerId, "Overpayment Test Customer");
    }

    @Test
    void explicitCustomerCreditAllocationGrantsOverpaymentCredit() {
        // Invoice 5 000, customer hands over 8 000 cash. The UI splits the
        // payment into a SALE_INVOICE allocation (5 000) and a CUSTOMER_CREDIT
        // allocation (3 000) — the latter triggers the OVERPAYMENT credit.
        UUID invoiceId = createInvoice(new BigDecimal("5000"));

        PaymentDto.PaymentResponse payment = paymentService.create(new PaymentDto.CreatePaymentRequest(
                "CASH_IN", customerId, new BigDecimal("8000"), "MRU",
                LocalDate.now(), "CASH", null, null, "Overpayment test", List.of(
                        new PaymentDto.AllocationRequest(
                                "SALE_INVOICE", invoiceId, new BigDecimal("5000"), null),
                        new PaymentDto.AllocationRequest(
                                "CUSTOMER_CREDIT", customerId, new BigDecimal("3000"), null))));

        PaymentDto.PaymentResponse confirmed = paymentService.confirm(payment.id(), null);
        assertThat(confirmed.status()).isEqualTo("CONFIRMED");

        BigDecimal creditBalance = jdbc.queryForObject("""
                SELECT COALESCE(SUM(remaining_amount), 0)
                FROM customer_credits
                WHERE party_id = ? AND status = 'ACTIVE' AND source = 'OVERPAYMENT'
                """, BigDecimal.class, customerId);
        assertThat(creditBalance).isEqualByComparingTo("3000");
    }

    @Test
    void paymentWithoutCustomerCreditAllocationDoesNotGrantOne() {
        UUID invoiceId = createInvoice(new BigDecimal("5000"));

        PaymentDto.PaymentResponse payment = paymentService.create(new PaymentDto.CreatePaymentRequest(
                "CASH_IN", customerId, new BigDecimal("5000"), "MRU",
                LocalDate.now(), "CASH", null, null, "Exact payment", List.of(
                        new PaymentDto.AllocationRequest(
                                "SALE_INVOICE", invoiceId, new BigDecimal("5000"), null))));
        paymentService.confirm(payment.id(), null);

        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM customer_credits
                WHERE party_id = ? AND source = 'OVERPAYMENT'
                """, Long.class, customerId);
        assertThat(count).isZero();
    }

    private UUID createInvoice(BigDecimal total) {
        return salesService.createInvoice(new SalesDto.CreateInvoiceRequest(
                customerId, null, LocalDate.now(), LocalDate.now().plusDays(30),
                null, "MRU", null,
                List.of(new SalesDto.LineRequest(productId, uomId, BigDecimal.ONE, total, BigDecimal.ZERO))
        )).id();
    }
}
