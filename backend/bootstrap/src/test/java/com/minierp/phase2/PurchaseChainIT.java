package com.minierp.phase2;

import com.minierp.MiniErpApplication;
import com.minierp.partner.api.CreatePartnerRequest;
import com.minierp.partner.internal.PartnerService;
import com.minierp.purchase.api.GoodsReceiptDto;
import com.minierp.purchase.api.PurchaseDto;
import com.minierp.purchase.internal.GoodsReceiptService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end purchase chain (no lot tracking): PO → confirm → convert → issue →
 * goods-receipt → avoir. Mirror of the sales chain ITs.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Purchase chain — convert, issue, receive, avoir + supplier return")
class PurchaseChainIT {

    @Autowired PurchaseService purchaseService;
    @Autowired GoodsReceiptService goodsReceiptService;
    @Autowired PartnerService partnerService;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;
    UUID supplierId;
    UUID productId;
    UUID warehouseId;
    UUID uomId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Purchase Chain Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "pchain-" + tenantId);
        TenantContext.set(tenantId);

        UUID categoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, 'CNT-PC', 'Count', now(), now(), 0)
                """, categoryId, tenantId);

        uomId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base,
                                   decimal_places, created_at, updated_at, version)
                VALUES (?, ?, ?, 'PCE-PC', 'Piece', 1, true, 0, now(), now(), 0)
                """, uomId, tenantId, categoryId);

        productId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, track_expiry, is_sellable, is_active,
                                      created_at, updated_at, version)
                VALUES (?, ?, 'SKU-PC-01', 'Plain Product', ?, 0, false, false, true, true,
                        now(), now(), 0)
                """, productId, tenantId, uomId);

        // Variant = SKU: purchase lines + receipt-to-stock resolve the variant; seed the
        // default variant reusing the product id.
        jdbc.update("""
                INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default,
                                              is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'SKU-PC-01', true, true, now(), now(), 0)
                """, productId, tenantId, productId);

        warehouseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouses (id, tenant_id, code, name, type, is_default, is_active,
                                        created_at, updated_at, version)
                VALUES (?, ?, 'WH-PC', 'Chain WH', 'MAIN', true, true, now(), now(), 0)
                """, warehouseId, tenantId);

        supplierId = partnerService.create(new CreatePartnerRequest(
                "E-PC-0001", false, true,
                "COMPANY", "Chain Supplier", null, "+22244002200",
                "Nouakchott", null, "NET30", "MRU", null,
                null, null, null, BigDecimal.ZERO
        )).id();
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    @Test
    void convertIssueReceive_fullChain() {
        PurchaseDto.PurchaseOrderDto po = purchaseService.createOrder(new PurchaseDto.CreatePurchaseOrderRequest(
                supplierId, warehouseId, LocalDate.now(), null, "MRU", "PO",
                List.of(new PurchaseDto.LineRequest(productId, uomId, BigDecimal.valueOf(10), new BigDecimal("50"), BigDecimal.ZERO))));
        purchaseService.confirmOrder(po.id());

        PurchaseDto.PurchaseInvoiceDto inv = purchaseService.convertOrderToInvoice(po.id(),
                new PurchaseDto.ConvertOrderToInvoiceRequest(null, null));
        assertThat(inv.status()).isEqualTo("DRAFT");
        // converting marks the PO CONVERTED
        assertThat(purchaseService.getOrder(po.id()).status()).isEqualTo("CONVERTED");

        inv = purchaseService.issueInvoice(inv.id());
        assertThat(inv.status()).isEqualTo("ISSUED");
        assertThat(inv.total()).isEqualByComparingTo("500");

        GoodsReceiptDto.GoodsReceiptResponse br = goodsReceiptService.receiveImmediately(inv.id(), warehouseId, null);
        assertThat(br.status()).isEqualTo("RECEIVED");
        assertThat(purchaseService.getInvoice(inv.id()).receptionStatus()).isEqualTo("RECEIVED");
        assertThat(onHand()).isEqualByComparingTo("10");
    }

    @Test
    void avoir_lettersPaidInvoice_returnsStock_andBlocksSecond() {
        UUID invoiceId = issuedInvoice(BigDecimal.valueOf(10), new BigDecimal("50"));
        goodsReceiptService.receiveImmediately(invoiceId, warehouseId, null);
        assertThat(onHand()).isEqualByComparingTo("10");

        // Pay it fully (facade), then issue an avoir.
        purchaseService.applyPayment(invoiceId, new BigDecimal("500"));
        assertThat(purchaseService.getInvoice(invoiceId).status()).isEqualTo("PAID");

        purchaseService.createPurchaseCreditNote(invoiceId, new PurchaseDto.CreatePurchaseCreditNoteRequest("Defective batch"));

        PurchaseDto.PurchaseInvoiceDto after = purchaseService.getInvoice(invoiceId);
        assertThat(after.status()).isEqualTo("PAID");                 // lettered by the avoir
        assertThat(after.balance()).isEqualByComparingTo("0");
        assertThat(after.paidAmount()).isEqualByComparingTo("0");      // cash detached
        assertThat(after.receptionStatus()).isEqualTo("RETURNED");
        assertThat(after.creditNoteCount()).isEqualTo(1);

        // A RETURN goods receipt sent the goods back → stock returns to zero.
        Long returnCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM goods_receipts WHERE purchase_invoice_id = ? AND type = 'RETURN'",
                Long.class, invoiceId);
        assertThat(returnCount).isEqualTo(1L);
        assertThat(onHand()).isEqualByComparingTo("0");

        // A second avoir is refused.
        assertThatThrownBy(() -> purchaseService.createPurchaseCreditNote(invoiceId,
                new PurchaseDto.CreatePurchaseCreditNoteRequest("again")))
                .hasMessageContaining("not_creditable");
    }

    @Test
    void avoir_blockedOnDraftInvoice() {
        PurchaseDto.PurchaseOrderDto po = purchaseService.createOrder(new PurchaseDto.CreatePurchaseOrderRequest(
                supplierId, warehouseId, LocalDate.now(), null, "MRU", null,
                List.of(new PurchaseDto.LineRequest(productId, uomId, BigDecimal.ONE, new BigDecimal("50"), BigDecimal.ZERO))));
        purchaseService.confirmOrder(po.id());
        PurchaseDto.PurchaseInvoiceDto draft = purchaseService.convertOrderToInvoice(po.id(),
                new PurchaseDto.ConvertOrderToInvoiceRequest(null, null));

        assertThatThrownBy(() -> purchaseService.createPurchaseCreditNote(draft.id(),
                new PurchaseDto.CreatePurchaseCreditNoteRequest("nope")))
                .hasMessageContaining("not_creditable");
    }

    private UUID issuedInvoice(BigDecimal qty, BigDecimal unitCost) {
        PurchaseDto.PurchaseOrderDto po = purchaseService.createOrder(new PurchaseDto.CreatePurchaseOrderRequest(
                supplierId, warehouseId, LocalDate.now(), null, "MRU", null,
                List.of(new PurchaseDto.LineRequest(productId, uomId, qty, unitCost, BigDecimal.ZERO))));
        purchaseService.confirmOrder(po.id());
        PurchaseDto.PurchaseInvoiceDto inv = purchaseService.convertOrderToInvoice(po.id(),
                new PurchaseDto.ConvertOrderToInvoiceRequest(LocalDate.now().plusDays(30), null));
        return purchaseService.issueInvoice(inv.id()).id();
    }

    private BigDecimal onHand() {
        return jdbc.queryForObject(
                "SELECT COALESCE(qty_on_hand, 0) FROM stocks WHERE warehouse_id = ? AND product_id = ?",
                BigDecimal.class, warehouseId, productId);
    }
}
