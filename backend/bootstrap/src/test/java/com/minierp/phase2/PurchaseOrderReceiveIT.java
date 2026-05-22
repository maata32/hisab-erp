package com.minierp.phase2;

import com.minierp.MiniErpApplication;
import com.minierp.partner.api.CreatePartnerRequest;
import com.minierp.partner.internal.PartnerService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the PO lifecycle: create → confirm → partial receive → full receive.
 * Verifies that:
 *  - Reception of a product with trackExpiry requires lotNumber + expirationDate
 *  - Partial reception sets PO status to PARTIALLY_RECEIVED
 *  - Final reception sets PO status to RECEIVED and posts stock movements
 *  - Stock on hand reflects the cumulative received quantity
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Purchase order receive — partial then full with lot tracking")
class PurchaseOrderReceiveIT {

    @Autowired PurchaseService purchaseService;
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
        PurchaseDto.PurchaseOrderDto po = createAndConfirmPo(BigDecimal.valueOf(10));
        UUID lineId = po.lines().get(0).id();

        assertThatThrownBy(() -> purchaseService.receive(po.id(), new PurchaseDto.ReceivePurchaseOrderRequest(
                null,
                List.of(new PurchaseDto.ReceiveLineRequest(lineId, BigDecimal.valueOf(4), null, null, null))
        ))).hasMessageContaining("lot_data_required");
    }

    @Test
    void partialThenFullReceiveTransitionsStatusAndStock() {
        PurchaseDto.PurchaseOrderDto po = createAndConfirmPo(BigDecimal.valueOf(10));
        UUID lineId = po.lines().get(0).id();
        LocalDate exp = LocalDate.now().plusDays(60);

        // 1st reception: 4 of 10
        PurchaseDto.ReceiptResult r1 = purchaseService.receive(po.id(),
                new PurchaseDto.ReceivePurchaseOrderRequest(null, List.of(
                        new PurchaseDto.ReceiveLineRequest(lineId, BigDecimal.valueOf(4),
                                "LOT-001", LocalDate.now().minusDays(1), exp))));
        assertThat(r1.status()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(r1.lines().get(0).lotId()).isNotNull();

        // 2nd reception: 6 → completes the line
        PurchaseDto.ReceiptResult r2 = purchaseService.receive(po.id(),
                new PurchaseDto.ReceivePurchaseOrderRequest(null, List.of(
                        new PurchaseDto.ReceiveLineRequest(lineId, BigDecimal.valueOf(6),
                                "LOT-002", null, exp.plusDays(5)))));
        assertThat(r2.status()).isEqualTo("RECEIVED");

        // Stock on hand should be 10 (= 4 + 6) at the PO warehouse for this product.
        BigDecimal onHand = jdbc.queryForObject(
                "SELECT COALESCE(qty_on_hand, 0) FROM stocks WHERE warehouse_id = ? AND product_id = ?",
                BigDecimal.class, warehouseId, productId);
        assertThat(onHand).isEqualByComparingTo("10");

        // Over-reception attempt should now fail.
        assertThatThrownBy(() -> purchaseService.receive(po.id(),
                new PurchaseDto.ReceivePurchaseOrderRequest(null, List.of(
                        new PurchaseDto.ReceiveLineRequest(lineId, BigDecimal.ONE,
                                "LOT-003", null, exp)))))
                .hasMessageContaining("po_not_receivable");
    }

    private PurchaseDto.PurchaseOrderDto createAndConfirmPo(BigDecimal qty) {
        PurchaseDto.PurchaseOrderDto created = purchaseService.createOrder(new PurchaseDto.CreatePurchaseOrderRequest(
                supplierId, warehouseId, LocalDate.now(), LocalDate.now().plusDays(7),
                "MRU", "Test PO",
                List.of(new PurchaseDto.LineRequest(productId, uomId, qty, new BigDecimal("100"), BigDecimal.ZERO))
        ));
        return purchaseService.confirmOrder(created.id());
    }
}
