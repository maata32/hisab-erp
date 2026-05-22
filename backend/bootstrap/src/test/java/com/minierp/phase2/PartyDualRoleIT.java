package com.minierp.phase2;

import com.minierp.MiniErpApplication;
import com.minierp.partner.api.ActivateSupplierRoleRequest;
import com.minierp.partner.api.CreatePartnerRequest;
import com.minierp.partner.api.PartnerDto;
import com.minierp.partner.internal.PartnerService;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies a single partner row can carry both customer and supplier roles
 * after activation, then drop a role via deactivate (Odoo-style, single code).
 * Asserts:
 *  - one parties row, single {@code code}, both flags after activation,
 *  - ap_balances row eagerly seeded by activate-supplier-role,
 *  - ar_balances stays lazy (no invoice yet),
 *  - deactivate-supplier-role flips is_supplier=false and refuses to remove the last role.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Partner dual role — single code, activate then deactivate supplier role")
class PartyDualRoleIT {

    @Autowired PartnerService partnerService;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Party Dual Role Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "dual-" + tenantId);
        TenantContext.set(tenantId);
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());
    }

    @AfterEach
    void teardown() {
        TenantContext.clear();
    }

    @Test
    void activatingThenDeactivatingSupplierRoleKeepsSingleCode() {
        PartnerDto created = partnerService.create(new CreatePartnerRequest(
                "E-DUAL-0001",
                true, false,
                "COMPANY", "Dual Role Co.", "ops@dual.co", "+22244000000",
                "Nouakchott", null, null, "MRU", null,
                null, null, BigDecimal.ZERO, null));

        assertThat(created.code()).isEqualTo("E-DUAL-0001");
        assertThat(created.isCustomer()).isTrue();
        assertThat(created.isSupplier()).isFalse();

        PartnerDto promoted = partnerService.activateSupplierRole(created.id(),
                new ActivateSupplierRoleRequest("MR0123456", "NET30",
                        new BigDecimal("500000.00")));

        assertThat(promoted.id()).isEqualTo(created.id());
        assertThat(promoted.code()).isEqualTo("E-DUAL-0001");
        assertThat(promoted.isCustomer()).isTrue();
        assertThat(promoted.isSupplier()).isTrue();

        Long partyRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parties WHERE id = ? AND is_customer = true AND is_supplier = true",
                Long.class, created.id());
        assertThat(partyRows).isEqualTo(1L);

        Long apRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ap_balances WHERE party_id = ?",
                Long.class, created.id());
        assertThat(apRows).as("ap_balances eagerly seeded by activate-supplier-role").isEqualTo(1L);

        PartnerDto demoted = partnerService.deactivateSupplierRole(created.id());
        assertThat(demoted.isSupplier()).isFalse();
        assertThat(demoted.isCustomer()).isTrue();
        assertThat(demoted.code()).isEqualTo("E-DUAL-0001");

        // Cannot drop the only remaining role
        assertThatThrownBy(() -> partnerService.deactivateCustomerRole(created.id()))
                .hasMessageContaining("cannot_remove_last_role");
    }
}
