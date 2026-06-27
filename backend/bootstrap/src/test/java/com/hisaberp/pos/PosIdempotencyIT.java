package com.hisaberp.pos;

import com.hisaberp.HisabErpApplication;
import com.hisaberp.pos.api.CreateSaleRequest;
import com.hisaberp.pos.api.CreateSaleRequest.PaymentRequest;
import com.hisaberp.pos.api.CreateSaleRequest.SaleLineRequest;
import com.hisaberp.pos.api.SaleDto;
import com.hisaberp.pos.internal.PosService;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the idempotency requirement from spec §3.1.3:
 * a second POST with the same idempotencyKey must return the original sale, not create a duplicate.
 */
@SpringBootTest(classes = HisabErpApplication.class)
@ActiveProfiles("test")
@DisplayName("POS — sale idempotency")
class PosIdempotencyIT {

    @Autowired
    PosService posService;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager em;

    UUID tenantId;
    UUID warehouseId;
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
        priceTierId = insertPriceTier(true);
        insertProductPrice(productId, uomId, priceTierId, new BigDecimal("100.00"));
        warehouseId = insertWarehouse();
        registerId = insertRegister(warehouseId, priceTierId);
        sessionId = openSession(registerId, cashierUserId);
    }

    @Test
    @DisplayName("Second submission with same idempotencyKey returns the original sale")
    void idempotent_sale_returns_same_id() {
        String key = "IDEM-KEY-" + UUID.randomUUID();
        CreateSaleRequest req = buildSaleRequest(key);

        SaleDto first = posService.createSale(req, cashierUserId);
        SaleDto second = posService.createSale(req, cashierUserId);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.number()).isEqualTo(first.number());
    }

    @Test
    @DisplayName("Second submission with same key does not insert a duplicate row")
    void idempotent_sale_no_duplicate_row() {
        String key = "IDEM-KEY-" + UUID.randomUUID();
        posService.createSale(buildSaleRequest(key), cashierUserId);
        posService.createSale(buildSaleRequest(key), cashierUserId);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales WHERE idempotency_key = ?", Integer.class, key);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Different idempotency keys produce separate sales with distinct numbers")
    void distinct_keys_produce_distinct_sales() {
        SaleDto s1 = posService.createSale(buildSaleRequest("KEY-A-" + UUID.randomUUID()), cashierUserId);
        SaleDto s2 = posService.createSale(buildSaleRequest("KEY-B-" + UUID.randomUUID()), cashierUserId);

        assertThat(s1.id()).isNotEqualTo(s2.id());
        assertThat(s1.number()).isNotEqualTo(s2.number());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private CreateSaleRequest buildSaleRequest(String key) {
        return new CreateSaleRequest(
                key,
                registerId, sessionId,
                null, null, null, null,
                List.of(new SaleLineRequest(productId, uomId, BigDecimal.ONE, null)),
                new PaymentRequest(new BigDecimal("100.00"), null, null, null));
    }

    private UUID insertOrg() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
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
                VALUES (?, ?, ?, 'Test Product', ?, 0.00, false, false, true, true,
                        true, now(), now(), 0)
                """, id, tenantId, "SKU-" + id, baseUomId);
        // Variant = SKU: POS resolves price + destocks by variant; seed the default
        // variant reusing the product id.
        jdbc.update("""
                INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default,
                                              is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, true, now(), now(), 0)
                """, id, tenantId, id, "SKU-" + id);
        return id;
    }

    private UUID insertPriceTier(boolean isDefault) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO price_tiers (id, tenant_id, code, name, is_default, is_active,
                                         created_at, updated_at, version)
                VALUES (?, ?, 'RETAIL', 'Retail', ?, true, now(), now(), 0)
                """, id, tenantId, isDefault);
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

    private UUID openSession(UUID registerId, UUID cashierUserId) {
        return posService.openSession(registerId, BigDecimal.ZERO, cashierUserId).id();
    }
}
