package com.minierp.phase1c;

import com.minierp.MiniErpApplication;
import com.minierp.lotexpiry.api.LotAllocation;
import com.minierp.lotexpiry.api.LotOperations;
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
 * Verifies FEFO (First-Expired First-Out) lot selection across successive sales.
 *
 * Scenario:
 *  - Product has two active lots: LOT-A expires in 10 days (qty=5), LOT-B expires in 30 days (qty=10).
 *  - Sale 1 requests qty=5 → should allocate entirely from LOT-A (nearest expiry).
 *  - Sale 2 requests qty=6 → LOT-A is now exhausted; should allocate entirely from LOT-B.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("FEFO: successive sales consume nearest-expiry lot first")
class FEFOSuccessiveSalesIT {

    @Autowired LotOperations lotOps;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;
    UUID productId;
    UUID warehouseId;
    UUID uomId;
    UUID categoryId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'FEFO Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "fefo-" + tenantId);

        TenantContext.set(tenantId);

        categoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, 'UNIT', 'Unit Category', now(), now(), 0)
                """, categoryId, tenantId);

        uomId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base,
                                   decimal_places, created_at, updated_at, version)
                VALUES (?, ?, ?, 'U', 'Unit', 1, true, 0, now(), now(), 0)
                """, uomId, tenantId, categoryId);

        productId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, track_expiry, shelf_life_days,
                                      is_sellable, is_active, created_at, updated_at, version)
                VALUES (?, ?, 'LOT-PROD-01', 'FEFO Test Product', ?, 0, true, true, 30,
                        true, true, now(), now(), 0)
                """, productId, tenantId, uomId);

        warehouseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouses (id, tenant_id, code, name, type, is_default, is_active,
                                        created_at, updated_at, version)
                VALUES (?, ?, 'WH-MAIN', 'Main Warehouse', 'MAIN', true, true, now(), now(), 0)
                """, warehouseId, tenantId);
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    @Test
    void fefoSelectsNearestExpiryFirstAcrossSuccessiveSales() {
        LocalDate today = LocalDate.now();

        // LOT-A: expires in 10 days, qty = 5
        UUID lotAId = lotOps.receiveLot(productId, warehouseId, uomId,
                "LOT-A", today.plusDays(10), today.minusDays(5),
                BigDecimal.valueOf(5), BigDecimal.ONE, null, null);

        // LOT-B: expires in 30 days, qty = 10
        UUID lotBId = lotOps.receiveLot(productId, warehouseId, uomId,
                "LOT-B", today.plusDays(30), today.minusDays(2),
                BigDecimal.valueOf(10), BigDecimal.ONE, null, null);

        // Sale 1: request 5 units → FEFO should take everything from LOT-A
        UUID sale1Id = UUID.randomUUID();
        List<LotAllocation> alloc1 = lotOps.selectFEFO(productId, warehouseId, BigDecimal.valueOf(5));

        assertThat(alloc1).hasSize(1);
        assertThat(alloc1.get(0).lotId()).isEqualTo(lotAId);
        assertThat(alloc1.get(0).quantity()).isEqualByComparingTo("5");

        lotOps.consumeAllocations(alloc1, "SALE", sale1Id);

        // Sale 2: request 6 units → LOT-A exhausted, should come from LOT-B
        UUID sale2Id = UUID.randomUUID();
        List<LotAllocation> alloc2 = lotOps.selectFEFO(productId, warehouseId, BigDecimal.valueOf(6));

        assertThat(alloc2).hasSize(1);
        assertThat(alloc2.get(0).lotId()).isEqualTo(lotBId);
        assertThat(alloc2.get(0).quantity()).isEqualByComparingTo("6");

        lotOps.consumeAllocations(alloc2, "SALE", sale2Id);

        // Verify remaining qty in LOT-B
        BigDecimal remaining = jdbc.queryForObject(
                "SELECT quantity_remaining FROM product_lots WHERE id = ?",
                BigDecimal.class, lotBId);
        assertThat(remaining).isEqualByComparingTo("4");
    }

    @Test
    void fefoSpansMultipleLotsWhenSingleLotInsufficient() {
        LocalDate today = LocalDate.now();

        UUID lotAId = lotOps.receiveLot(productId, warehouseId, uomId,
                "LOT-X", today.plusDays(5), null,
                BigDecimal.valueOf(3), BigDecimal.ONE, null, null);

        UUID lotBId = lotOps.receiveLot(productId, warehouseId, uomId,
                "LOT-Y", today.plusDays(20), null,
                BigDecimal.valueOf(7), BigDecimal.ONE, null, null);

        // Request 8 — must span both lots
        List<LotAllocation> alloc = lotOps.selectFEFO(productId, warehouseId, BigDecimal.valueOf(8));

        assertThat(alloc).hasSize(2);
        // First allocation from nearest-expiry lot
        assertThat(alloc.get(0).lotId()).isEqualTo(lotAId);
        assertThat(alloc.get(0).quantity()).isEqualByComparingTo("3");
        // Second from further-expiry lot
        assertThat(alloc.get(1).lotId()).isEqualTo(lotBId);
        assertThat(alloc.get(1).quantity()).isEqualByComparingTo("5");
    }
}
