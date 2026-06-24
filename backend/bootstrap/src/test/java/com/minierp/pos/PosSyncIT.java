package com.minierp.pos;

import com.minierp.MiniErpApplication;
import com.minierp.pos.api.CreateSaleRequest;
import com.minierp.pos.api.CreateSaleRequest.PaymentRequest;
import com.minierp.pos.api.CreateSaleRequest.SaleLineRequest;
import com.minierp.pos.api.SyncSalesResponse;
import com.minierp.pos.internal.PosService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the mandatory spec test: offline sync of 1000 sales, idempotent on replay.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("POS — offline sync batch (1 000 sales)")
class PosSyncIT {

    @Autowired
    PosService posService;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager em;

    UUID tenantId;
    UUID registerId;
    UUID sessionId;
    UUID productId;
    UUID uomId;
    UUID priceTierId;
    UUID cashierUserId;

    @BeforeEach
    void seed() {
        tenantId = insertOrg();
        TenantContext.set(tenantId);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());

        cashierUserId = UUID.randomUUID();
        uomId = insertUomCategory_and_Uom();
        productId = insertProduct(uomId);
        priceTierId = insertPriceTier();
        insertProductPrice(productId, uomId, priceTierId, new BigDecimal("10.00"));
        UUID warehouseId = insertWarehouse();
        registerId = insertRegister(warehouseId, priceTierId);
        sessionId = posService.openSession(registerId, BigDecimal.ZERO, cashierUserId).id();
    }

    @Test
    @DisplayName("Batch of 1 000 sales: all accepted, no duplicates")
    void sync_1000_sales_all_accepted() {
        List<CreateSaleRequest> batch = buildBatch(1000);

        SyncSalesResponse response = posService.syncSales(batch, cashierUserId);

        assertThat(response.results()).hasSize(1000);
        assertThat(response.results()).allMatch(r -> "ACCEPTED".equals(r.status()));
        assertThat(response.results()).map(SyncSalesResponse.SyncResult::saleId).doesNotContainNull();

        Integer dbCount = jdbc.queryForObject("SELECT COUNT(*) FROM sales WHERE session_id = ?",
                Integer.class, sessionId);
        assertThat(dbCount).isEqualTo(1000);
    }

    @Test
    @DisplayName("Replaying the same 1 000 sales returns the same IDs — no new rows created")
    void sync_1000_sales_idempotent_replay() {
        List<CreateSaleRequest> batch = buildBatch(1000);

        SyncSalesResponse first = posService.syncSales(batch, cashierUserId);
        SyncSalesResponse second = posService.syncSales(batch, cashierUserId);

        List<UUID> firstIds = first.results().stream().map(SyncSalesResponse.SyncResult::saleId).toList();
        List<UUID> secondIds = second.results().stream().map(SyncSalesResponse.SyncResult::saleId).toList();
        assertThat(secondIds).containsExactlyElementsOf(firstIds);

        Integer dbCount = jdbc.queryForObject("SELECT COUNT(*) FROM sales WHERE session_id = ?",
                Integer.class, sessionId);
        assertThat(dbCount).isEqualTo(1000);
    }

    @Test
    @DisplayName("Batch with one failing sale: the others still commit (no rollback-only poisoning)")
    void sync_batch_isolates_failing_sale() {
        UUID unpricedVariant = insertProduct(uomId); // product + variant, but NO price seeded → no_price
        String goodKey = "OK-" + tenantId;
        String badKey = "BAD-" + tenantId;
        List<CreateSaleRequest> batch = List.of(
                new CreateSaleRequest(goodKey, registerId, sessionId, null, null, null, null,
                        List.of(new SaleLineRequest(productId, uomId, BigDecimal.ONE, null)),
                        new PaymentRequest(new BigDecimal("10.00"), null, null, null)),
                new CreateSaleRequest(badKey, registerId, sessionId, null, null, null, null,
                        List.of(new SaleLineRequest(unpricedVariant, uomId, BigDecimal.ONE, null)),
                        new PaymentRequest(new BigDecimal("10.00"), null, null, null)));

        // Must NOT throw (previously the failing sale marked the shared tx rollback-only → 500).
        SyncSalesResponse resp = posService.syncSales(batch, cashierUserId);

        SyncSalesResponse.SyncResult good = resp.results().stream()
                .filter(r -> goodKey.equals(r.idempotencyKey())).findFirst().orElseThrow();
        SyncSalesResponse.SyncResult bad = resp.results().stream()
                .filter(r -> badKey.equals(r.idempotencyKey())).findFirst().orElseThrow();
        assertThat(good.status()).isEqualTo("ACCEPTED");
        assertThat(good.saleId()).isNotNull();
        assertThat(bad.status()).isEqualTo("ERROR");

        // The good sale is persisted despite the bad one failing in the same batch.
        Integer dbCount = jdbc.queryForObject("SELECT COUNT(*) FROM sales WHERE session_id = ?",
                Integer.class, sessionId);
        assertThat(dbCount).isEqualTo(1);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<CreateSaleRequest> buildBatch(int count) {
        List<CreateSaleRequest> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String key = "SYNC-" + i + "-" + tenantId;
            batch.add(new CreateSaleRequest(
                    key, registerId, sessionId,
                    null, null, null, null,
                    List.of(new SaleLineRequest(productId, uomId, BigDecimal.ONE, null)),
                    new PaymentRequest(new BigDecimal("10.00"), null, null, null)));
        }
        return batch;
    }

    private UUID insertOrg() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Sync Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, id, "org-" + id);
        return id;
    }

    private UUID insertUomCategory_and_Uom() {
        UUID catId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, 'COUNT', 'Count', now(), now(), 0)
                """, catId, tenantId);
        UUID uomId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base,
                                  decimal_places, created_at, updated_at, version)
                VALUES (?, ?, ?, 'PCE', 'Piece', 1, true, 0, now(), now(), 0)
                """, uomId, tenantId, catId);
        return uomId;
    }

    private UUID insertProduct(UUID baseUomId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, tracks_serial, is_sellable, is_purchasable,
                                      is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'Sync Product', ?, 0.00, false, false, true, true,
                        true, now(), now(), 0)
                """, id, tenantId, "SKU-SYNC-" + id, baseUomId);
        // Variant = SKU: POS resolves price + destocks by variant; seed the default
        // variant reusing the product id.
        jdbc.update("""
                INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default,
                                              is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, true, now(), now(), 0)
                """, id, tenantId, id, "SKU-SYNC-" + id);
        return id;
    }

    private UUID insertPriceTier() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO price_tiers (id, tenant_id, code, name, is_default, is_active,
                                         created_at, updated_at, version)
                VALUES (?, ?, 'RETAIL', 'Retail', true, true, now(), now(), 0)
                """, id, tenantId);
        return id;
    }

    private void insertProductPrice(UUID productId, UUID uomId, UUID tierId, BigDecimal amount) {
        // Prices are variant-keyed (variant_id NOT NULL); reuse the product id as its variant id.
        jdbc.update("""
                INSERT INTO product_prices (id, tenant_id, variant_id, product_id, uom_id, price_tier_id,
                                            amount, currency, tax_inclusive, valid_from,
                                            created_at, updated_at, version)
                VALUES (uuid_generate_v4(), ?, ?, ?, ?, ?, ?, 'MRU', false, '2000-01-01',
                        now(), now(), 0)
                """, tenantId, productId, productId, uomId, tierId, amount);
    }

    private UUID insertWarehouse() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouses (id, tenant_id, code, name, is_default, is_active,
                                        created_at, updated_at, version)
                VALUES (?, ?, 'MAIN', 'Main Warehouse', true, true, now(), now(), 0)
                """, id, tenantId);
        return id;
    }

    private UUID insertRegister(UUID warehouseId, UUID priceTierId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cash_registers (id, tenant_id, code, name, warehouse_id,
                                            default_price_tier_id, receipt_width_mm, is_active,
                                            created_at, updated_at, version)
                VALUES (?, ?, 'REG-01', 'Register 1', ?, ?, 80, true, now(), now(), 0)
                """, id, tenantId, warehouseId, priceTierId);
        return id;
    }
}
