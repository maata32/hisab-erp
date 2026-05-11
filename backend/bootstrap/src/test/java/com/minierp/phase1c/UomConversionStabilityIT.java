package com.minierp.phase1c;

import com.minierp.MiniErpApplication;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.uom.api.UomLookup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that UoM conversions are numerically stable (no floating-point drift)
 * and that cross-category conversions are rejected.
 *
 * Uses: KG (base) and G (ratio_to_base=0.001) in the mass category.
 * Litre is in a separate volume category → cross-category conversion must throw.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("UoM conversion: numeric stability and cross-category guard")
class UomConversionStabilityIT {

    @Autowired UomLookup uomLookup;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;
    UUID categoryMassId;
    UUID categoryVolumeId;
    UUID kgId;
    UUID gId;
    UUID litreId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'UOM Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "uom-" + tenantId);
        TenantContext.set(tenantId);

        categoryMassId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, 'MASS', 'Mass', now(), now(), 0)
                """, categoryMassId, tenantId);

        categoryVolumeId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, 'VOL', 'Volume', now(), now(), 0)
                """, categoryVolumeId, tenantId);

        // KG: base unit, ratio_to_base=1, decimal_places=3
        kgId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base,
                                   decimal_places, created_at, updated_at, version)
                VALUES (?, ?, ?, 'KG', 'Kilogram', 1, true, 3, now(), now(), 0)
                """, kgId, tenantId, categoryMassId);

        // G: 1g = 0.001 kg → ratio_to_base=0.001, decimal_places=0
        gId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base,
                                   decimal_places, created_at, updated_at, version)
                VALUES (?, ?, ?, 'G', 'Gram', 0.001, false, 0, now(), now(), 0)
                """, gId, tenantId, categoryMassId);

        // Litre: separate volume category
        litreId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base,
                                   decimal_places, created_at, updated_at, version)
                VALUES (?, ?, ?, 'L', 'Litre', 1, true, 3, now(), now(), 0)
                """, litreId, tenantId, categoryVolumeId);
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("500 g → kg gives exact 0.500")
    void gramsToKilograms() {
        BigDecimal result = uomLookup.convert(BigDecimal.valueOf(500), gId, kgId);
        assertThat(result).isEqualByComparingTo("0.500");
    }

    @Test
    @DisplayName("2500 g → kg gives 2.500")
    void gramsToKilogramsLarge() {
        BigDecimal result = uomLookup.convert(BigDecimal.valueOf(2500), gId, kgId);
        assertThat(result).isEqualByComparingTo("2.500");
    }

    @Test
    @DisplayName("2.5 kg → g gives 2500")
    void kilogramsToGrams() {
        BigDecimal result = uomLookup.convert(new BigDecimal("2.5"), kgId, gId);
        assertThat(result).isEqualByComparingTo("2500");
    }

    @Test
    @DisplayName("Cross-category conversion (kg → litre) throws exception")
    void crossCategoryConversionThrows() {
        assertThatThrownBy(() -> uomLookup.convert(BigDecimal.ONE, kgId, litreId))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Large quantity conversion remains numerically stable")
    void largeQuantityStability() {
        BigDecimal result = uomLookup.convert(BigDecimal.valueOf(999_999), gId, kgId);
        assertThat(result).isEqualByComparingTo("999.999");
    }
}
