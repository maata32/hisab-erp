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
 * Exercises the new purchase reception chain: PO → confirm → convert → issue →
 * goods-receipt (BRC). Verifies that:
 *  - Reception of a product with trackExpiry requires lotNumber + expirationDate
 *  - Partial reception leaves the invoice PARTIALLY_RECEIVED
 *  - Full reception moves the invoice to RECEIVED and posts stock movements
 *  - Stock on hand reflects the cumulative received quantity
 *  - Reception is refused once the invoice is fully received
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Goods receipt — partial then full with lot tracking")
class PurchaseOrderReceiveIT {

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
                VALUES (?, ?, 'Purchase Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "purchase-" + tenantId);

        TenantContext.set(tenantId);

        UUID categoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, 'CNT-PUR', 'Count', now(), now(), 0)
                """, categoryId, tenantId);

        uomId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base,
                                   decimal_places, created_at, updated_at, version)
                VALUES (?, ?, ?, 'PCE-PUR', 'Piece', 1, true, 0, now(), now(), 0)
                """, uomId, tenantId, categoryId);

        productId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, track_expiry, shelf_life_days,
                                      is_sellable, is_active, created_at, updated_at, version)
                VALUES (?, ?, 'SKU-PUR-01', 'Purchase Lot Product', ?, 0, true, true, 60,
                        true, true, now(), now(), 0)
                """, productId, tenantId, uomId);

        warehouseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouses (id, tenant_id, code, name, type, is_default, is_active,
                                        created_at, updated_at, version)
                VALUES (?, ?, 'WH-PUR', 'Purchase WH', 'MAIN', true, true, now(), now(), 0)
                """, warehouseId, tenantId);

        supplierId = partnerService.create(new CreatePartnerRequest(
                "E-TST-0001",
                false, true,
                "COMPANY", "ACME Supplier", null, "+22244001100",
                "Nouakchott", null, "NET30", "MRU", null,
                null, null, null, BigDecimal.ZERO
        )).id();
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    @Test
    void receiveTrackedProductRequiresLotData() {
        UUID invoiceId = createIssuedInvoice(BigDecimal.valueOf(10));
        GoodsReceiptDto.GoodsReceiptResponse br = goodsReceiptService.create(
                new GoodsReceiptDto.CreateGoodsReceiptRequest(supplierId, invoiceId, warehouseId, LocalDate.now(), null, null),
                null);
        UUID lineId = br.lines().get(0).id();

        assertThatThrownBy(() -> goodsReceiptService.recordReceipt(br.id(),
                new GoodsReceiptDto.RecordReceiptRequest(List.of(
                        new GoodsReceiptDto.LineReceived(lineId, BigDecimal.valueOf(4), null, null, null)), null),
                null)).hasMessageContaining("lot_data_required");
    }

    @Test
    void partialThenFullReceiveTransitionsStatusAndStock() {
        UUID invoiceId = createIssuedInvoice(BigDecimal.valueOf(10));
        LocalDate exp = LocalDate.now().plusDays(60);

        // 1st reception: 4 of 10
        GoodsReceiptDto.GoodsReceiptResponse br1 = goodsReceiptService.create(
                new GoodsReceiptDto.CreateGoodsReceiptRequest(supplierId, invoiceId, warehouseId, LocalDate.now(), null, null),
                null);
        goodsReceiptService.recordReceipt(br1.id(),
                new GoodsReceiptDto.RecordReceiptRequest(List.of(
                        new GoodsReceiptDto.LineReceived(br1.lines().get(0).id(), BigDecimal.valueOf(4),
                                "LOT-001", LocalDate.now().minusDays(1), exp)), null),
                null);
        assertThat(purchaseService.getInvoice(invoiceId).receptionStatus()).isEqualTo("PARTIALLY_RECEIVED");

        // 2nd reception: 6 → completes the invoice
        GoodsReceiptDto.GoodsReceiptResponse br2 = goodsReceiptService.create(
                new GoodsReceiptDto.CreateGoodsReceiptRequest(supplierId, invoiceId, warehouseId, LocalDate.now(), null, null),
                null);
        assertThat(br2.lines().get(0).quantityOrdered()).isEqualByComparingTo("6");
        goodsReceiptService.recordReceipt(br2.id(),
                new GoodsReceiptDto.RecordReceiptRequest(List.of(
                        new GoodsReceiptDto.LineReceived(br2.lines().get(0).id(), BigDecimal.valueOf(6),
                                "LOT-002", null, exp.plusDays(5))), null),
                null);
        assertThat(purchaseService.getInvoice(invoiceId).receptionStatus()).isEqualTo("RECEIVED");

        // Stock on hand should be 10 (= 4 + 6) at the warehouse for this product.
        BigDecimal onHand = jdbc.queryForObject(
                "SELECT COALESCE(qty_on_hand, 0) FROM stocks WHERE warehouse_id = ? AND product_id = ?",
                BigDecimal.class, warehouseId, productId);
        assertThat(onHand).isEqualByComparingTo("10");

        // A new reception is refused once the invoice is fully received.
        assertThatThrownBy(() -> goodsReceiptService.create(
                new GoodsReceiptDto.CreateGoodsReceiptRequest(supplierId, invoiceId, warehouseId, LocalDate.now(), null, null),
                null)).hasMessageContaining("invoice_not_receivable");
    }

    /** Create a confirmed PO, convert it to a draft invoice and issue it. */
    private UUID createIssuedInvoice(BigDecimal qty) {
        PurchaseDto.PurchaseOrderDto created = purchaseService.createOrder(new PurchaseDto.CreatePurchaseOrderRequest(
                supplierId, warehouseId, LocalDate.now(), LocalDate.now().plusDays(7),
                "MRU", "Test PO",
                List.of(new PurchaseDto.LineRequest(productId, uomId, qty, new BigDecimal("100"), BigDecimal.ZERO))
        ));
        purchaseService.confirmOrder(created.id());
        PurchaseDto.PurchaseInvoiceDto inv = purchaseService.convertOrderToInvoice(created.id(),
                new PurchaseDto.ConvertOrderToInvoiceRequest(LocalDate.now().plusDays(30), "SUP-REF-1"));
        purchaseService.issueInvoice(inv.id());
        return inv.id();
    }
}
