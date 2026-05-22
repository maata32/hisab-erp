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

/**
 * Verifies a single partner row can carry both customer and supplier roles after
 * activation (Odoo-style). Creates a customer-only partner, activates the
 * supplier role, asserts:
 *  - the parties row stays unique (same id) but both is_customer and is_supplier are true,
 *  - exactly one ap_balances row exists for the party (eagerly seeded by activation),
 *  - no ar_balances row yet (created lazily on first invoice, not on partner create),
 *  - the PartnerDto exposes isSupplier=true and the supplierCode.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Partner dual role — customer-only partner can also become supplier")
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
    void activatingSupplierRoleKeepsSingleRowAndAddsApBalance() {
        PartnerDto created = partnerService.create(new CreatePartnerRequest(
                true, false,
                "C-DUAL-0001", null,
                "COMPANY", "Dual Role Co.", "ops@dual.co", "+22244000000",
                "Nouakchott", null, null, "MRU", null,
                null, null, BigDecimal.ZERO, null));

        assertThat(created.isCustomer()).isTrue();
        assertThat(created.isSupplier()).isFalse();
        assertThat(created.supplierCode()).isNull();

        PartnerDto promoted = partnerService.activateSupplierRole(created.id(),
                new ActivateSupplierRoleRequest(
                        "F-DUAL-0001", "MR0123456", "NET30",
                        new BigDecimal("500000.00")));

        assertThat(promoted.id()).isEqualTo(created.id());
        assertThat(promoted.isCustomer()).isTrue();
        assertThat(promoted.isSupplier()).isTrue();
        assertThat(promoted.supplierCode()).isEqualTo("F-DUAL-0001");

        Long partyRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parties WHERE id = ? AND is_customer = true AND is_supplier = true",
                Long.class, created.id());
        assertThat(partyRows).isEqualTo(1L);

        Long arRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ar_balances WHERE party_id = ?",
                Long.class, created.id());
        assertThat(arRows).as("ar_balances is lazy — no invoice yet").isEqualTo(0L);

        Long apRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ap_balances WHERE party_id = ?",
                Long.class, created.id());
        assertThat(apRows).as("ap_balances eagerly seeded by activate-supplier-role").isEqualTo(1L);
    }
}
