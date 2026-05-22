package com.minierp.phase2;

import com.minierp.MiniErpApplication;
import com.minierp.partner.api.CreatePartnerRequest;
import com.minierp.partner.internal.PartnerService;
import com.minierp.payment.api.PaymentDto;
import com.minierp.payment.internal.PaymentService;
import com.minierp.purchase.api.PurchaseDto;
import com.minierp.purchase.internal.PurchaseService;
import com.minierp.shared.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: create a supplier invoice, post a supplier payment, allocate the payment
 * to the purchase invoice and confirm it. Verifies that:
 *  - PurchaseInvoice.paidAmount / status update via PurchaseInvoiceOperations
 *  - SupplierBalance reflects the invoiced and paid amounts.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Supplier payment allocation — invoice paid, balance settled")
class SupplierPaymentAllocationIT {

    @Autowired PurchaseService purchaseService;
    @Autowired PaymentService paymentService;
    @Autowired PartnerService partnerService;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;
    UUID supplierId;
    UUID productId;
    UUID uomId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Sup Pay Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "suppay-" + tenantId);

        TenantContext.set(tenantId);

        UUID categoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, 'CNT-SUP', 'Count', now(), now(), 0)
                """, categoryId, tenantId);

        uomId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base,
                                   decimal_places, created_at, updated_at, version)
                VALUES (?, ?, ?, 'PCE-SUP', 'Piece', 1, true, 0, now(), now(), 0)
                """, uomId, tenantId, categoryId);

        productId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, track_expiry, is_sellable, is_active,
                                      created_at, updated_at, version)
                VALUES (?, ?, 'SKU-SUP-01', 'Sup Pay Product', ?, 0, false, false,
                        true, true, now(), now(), 0)
                """, productId, tenantId, uomId);

        supplierId = partnerService.create(new CreatePartnerRequest(
                false, true,
                null, "F-SUP-0001",
                "COMPANY", "Pay Test Supplier", null, "+22244002200",
                null, null, null, "MRU", null,
                null, null, null, BigDecimal.ZERO
        )).id();
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    @Test
    void fullPaymentMarksInvoicePaidAndSupplierBalanceZero() {
        // Create supplier invoice of 800,000 MRU
        PurchaseDto.PurchaseInvoiceDto inv = purchaseService.createInvoice(new PurchaseDto.CreatePurchaseInvoiceRequest(
                supplierId, null, "SUP-2026-0010",
                LocalDate.now(), LocalDate.now().plusDays(30),
                "MRU", "Test supplier invoice",
                List.of(new PurchaseDto.LineRequest(productId, uomId,
                        BigDecimal.valueOf(8), new BigDecimal("100000"), BigDecimal.ZERO))
        ));
        assertThat(inv.total()).isEqualByComparingTo("800000.00");
        assertThat(partnerService.getApBalance(supplierId).balance()).isEqualByComparingTo("800000.00");

        // Pay it via SUPPLIER_PAYMENT → BANK_TRANSFER
        PaymentDto.PaymentResponse payment = paymentService.create(new PaymentDto.CreatePaymentRequest(
                "SUPPLIER_PAYMENT", supplierId, new BigDecimal("800000.00"), "MRU",
                LocalDate.now(), "BANK_TRANSFER", "VIR-2026-04-30-001", "BMI-12345",
                "Supplier settlement",
                List.of(new PaymentDto.AllocationRequest("PURCHASE_INVOICE", inv.id(),
                        new BigDecimal("800000.00"), null))
        ));

        paymentService.confirm(payment.id(), null);

        PurchaseDto.PurchaseInvoiceDto reloaded = purchaseService.getInvoice(inv.id());
        assertThat(reloaded.status()).isEqualTo("PAID");
        assertThat(reloaded.paidAmount()).isEqualByComparingTo("800000.00");
        assertThat(reloaded.balance()).isEqualByComparingTo("0.00");

        assertThat(partnerService.getApBalance(supplierId).balance()).isEqualByComparingTo("0.00");
    }
}
