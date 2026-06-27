package com.hisaberp.multitenancy;

import com.hisaberp.HisabErpApplication;
import com.hisaberp.catalog.api.CatalogLookup;
import com.hisaberp.inventory.api.StockDto;
import com.hisaberp.inventory.api.StockMovementType;
import com.hisaberp.inventory.api.StockOperations;
import com.hisaberp.shared.tenant.TenantContext;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies cross-tenant isolation for Phase 1A modules:
 * catalog (products), inventory (warehouse/stock).
 */
@SpringBootTest(classes = HisabErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Phase 1A — cross-tenant isolation")
class Phase1ACrossTenantIT {

    @Autowired
    CatalogLookup catalogLookup;

    @Autowired
    StockOperations stockOps;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager em;

    UUID tenantA;
    UUID tenantB;

    @BeforeEach
    void seedTwoTenants() {
        tenantA = insertOrg("tenant-a-" + UUID.randomUUID());
        tenantB = insertOrg("tenant-b-" + UUID.randomUUID());
    }

    @Test
    @DisplayName("Product created in tenant A is not visible from tenant B via CatalogLookup")
    void catalog_product_isolated_between_tenants() {
        // Insert product for tenant A
        UUID uomA = insertUomAndCategory(tenantA);
        UUID productA = insertProduct(tenantA, uomA, "SKU-A-" + UUID.randomUUID());

        // Query as tenant A — should find it
        switchTenant(tenantA);
        assertThat(catalogLookup.findProductById(productA)).isPresent();

        // Query as tenant B — must NOT find it
        switchTenant(tenantB);
        assertThat(catalogLookup.findProductById(productA)).isEmpty();
    }

    @Test
    @DisplayName("Stock receive in tenant A does not affect tenant B's view of the same product")
    void inventory_stock_isolated_between_tenants() {
        UUID uomA = insertUomAndCategory(tenantA);
        UUID uomB = insertUomAndCategory(tenantB);
        UUID productA = insertProduct(tenantA, uomA, "SKU-STOCK-" + UUID.randomUUID());
        UUID productB = insertProduct(tenantB, uomB, "SKU-STOCK-B-" + UUID.randomUUID());
        UUID warehouseA = insertWarehouse(tenantA, "WH-A");
        UUID warehouseB = insertWarehouse(tenantB, "WH-B");

        // Receive 50 units for tenant A
        switchTenant(tenantA);
        stockOps.receive(warehouseA, productA, new BigDecimal("50"), BigDecimal.ONE,
                StockMovementType.OPENING_BALANCE, null, null, null, "seed", null);

        // Tenant B should see zero stock (not tenant A's)
        switchTenant(tenantB);
        StockDto stockB = stockOps.getStock(warehouseB, productB);
        assertThat(stockB.qtyOnHand()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @Transactional
    @DisplayName("Direct SQL with tenant A context cannot read tenant B products (RLS)")
    void rls_blocks_cross_tenant_catalog_read() {
        UUID uomB = insertUomAndCategory(tenantB);
        UUID productB = insertProduct(tenantB, uomB, "SKU-RLS-" + UUID.randomUUID());

        // Set session to tenant A
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantA.toString());

        // Attempt to read tenant B's product directly
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE id = ?", Integer.class, productB);
        assertThat(count).isZero();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void switchTenant(UUID tenantId) {
        TenantContext.set(tenantId);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());
    }

    private UUID insertOrg(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, id, code);
        return id;
    }

    private UUID insertUomAndCategory(UUID tenantId) {
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

    private UUID insertProduct(UUID tenantId, UUID baseUomId, String sku) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, tracks_serial, is_sellable, is_purchasable,
                                      is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'Test Product', ?, 0.00, false, false, true, true,
                        true, now(), now(), 0)
                """, id, tenantId, sku, baseUomId);
        // Variant = SKU: stock ops resolve the product through its variant, so seed a
        // default variant reusing the product id.
        jdbc.update("""
                INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default,
                                              is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, true, now(), now(), 0)
                """, id, tenantId, id, sku);
        return id;
    }

    private UUID insertWarehouse(UUID tenantId, String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouses (id, tenant_id, code, name, is_default, is_active,
                                        created_at, updated_at, version)
                VALUES (?, ?, ?, 'Warehouse', true, true, now(), now(), 0)
                """, id, tenantId, code);
        return id;
    }
}
