package com.minierp.phase1b;

import com.minierp.MiniErpApplication;
import com.minierp.payment.api.PaymentDto;
import com.minierp.payment.internal.PaymentService;
import com.minierp.sales.api.InvoiceSummary;
import com.minierp.sales.api.SalesDto;
import com.minierp.sales.internal.SalesService;
import com.minierp.shared.tenant.TenantContext;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory spec test: auto-allocate a payment across 3 unpaid invoices (FIFO by due date).
 * Inv1=100, Inv2=200, Inv3=150 — payment of 350 → Inv1 fully paid, Inv2 fully paid, Inv3 partially paid.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Payment auto-allocation — FIFO across 3 invoices")
class PaymentAllocationIT {

    @Autowired
    PaymentService paymentService;

    @Autowired
    SalesService salesService;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager em;

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
                VALUES (?, ?, 'Payment Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "paytest-" + tenantId);

        TenantContext.set(tenantId);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());

        customerId = UUID.randomUUID();
        uomId = UUID.randomUUID();
        productId = UUID.randomUUID();

        jdbc.update("INSERT INTO customers (id, tenant_id, code, name, active, created_at, updated_at, version) VALUES (?,?,?,?,true,now(),now(),0)",
                customerId, tenantId, "C-PAYTEST", "Payment Test Customer");
    }

    @Test
    void autoAllocateFifoAcrossThreeInvoices() {
        // Create 3 invoices with increasing due dates (FIFO order: inv1 oldest)
        UUID inv1 = createInvoice(new BigDecimal("100.00"), LocalDate.now().minusDays(30));
        UUID inv2 = createInvoice(new BigDecimal("200.00"), LocalDate.now().minusDays(20));
        UUID inv3 = createInvoice(new BigDecimal("150.00"), LocalDate.now().minusDays(10));

        // Create payment of 350 (covers inv1=100 + inv2=200 + partial inv3=50)
        PaymentDto.PaymentResponse payment = paymentService.create(new PaymentDto.CreatePaymentRequest(
                "CUSTOMER_RECEIPT", customerId, new BigDecimal("350.00"), "MRU",
                LocalDate.now(), "CASH", null, null, "Auto-allocate test", null
        ));

        // Auto-allocate FIFO
        paymentService.autoAllocate(new PaymentDto.AutoAllocateRequest(customerId, payment.id(), null));

        // Confirm
        PaymentDto.PaymentResponse confirmed = paymentService.confirm(payment.id(), null);

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.allocations()).hasSize(3);

        Map<UUID, BigDecimal> allocByTarget = confirmed.allocations().stream()
                .collect(Collectors.toMap(
                        PaymentDto.AllocationDto::targetId,
                        PaymentDto.AllocationDto::allocatedAmount));

        assertThat(allocByTarget.get(inv1)).isEqualByComparingTo("100.00");
        assertThat(allocByTarget.get(inv2)).isEqualByComparingTo("200.00");
        assertThat(allocByTarget.get(inv3)).isEqualByComparingTo("50.00");

        // Check invoice statuses via InvoiceOperations interface
        assertThat(salesService.findById(inv1)).map(InvoiceSummary::status).hasValue("PAID");
        assertThat(salesService.findById(inv2)).map(InvoiceSummary::status).hasValue("PAID");
        assertThat(salesService.findById(inv3)).map(InvoiceSummary::status).hasValue("ISSUED");
    }

    private UUID createInvoice(BigDecimal total, LocalDate dueDate) {
        SalesDto.CreateInvoiceRequest req = new SalesDto.CreateInvoiceRequest(
                customerId, null, LocalDate.now(), dueDate, null, "MRU", null,
                List.of(new SalesDto.LineRequest(productId, uomId, BigDecimal.ONE, total, BigDecimal.ZERO))
        );
        return salesService.createInvoice(req).id();
    }
}
