package com.minierp.phase2;

import com.minierp.MiniErpApplication;
import com.minierp.customer.api.ActivateSupplierRoleRequest;
import com.minierp.customer.api.CreateCustomerRequest;
import com.minierp.customer.api.CustomerDto;
import com.minierp.customer.internal.CustomerService;
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
 * Verifies a single party row can carry both customer and supplier roles after activation
 * (Odoo/SAP style). Creates a customer, activates the supplier role, asserts:
 *  - the parties row stays unique (same id) but both is_customer and is_supplier are true,
 *  - exactly one ar_balances row exists for the party,
 *  - exactly one ap_balances row exists for the party,
 *  - the CustomerDto exposes alsoSupplier=true and the supplierCode.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Party dual role — customer can also become supplier")
class PartyDualRoleIT {

    @Autowired CustomerService customerService;
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
        CustomerDto created = customerService.create(new CreateCustomerRequest(
                "C-DUAL-0001", "COMPANY", "Dual Role Co.", "ops@dual.co", "+22244000000",
                "Nouakchott", BigDecimal.ZERO, "MRU", null, null, null));

        assertThat(created.alsoSupplier()).isFalse();
        assertThat(created.supplierCode()).isNull();

        CustomerDto promoted = customerService.activateSupplierRole(created.id(),
                new ActivateSupplierRoleRequest(
                        "F-DUAL-0001", "MR0123456", "NET30",
                        new BigDecimal("500000.00")));

        assertThat(promoted.id()).isEqualTo(created.id());
        assertThat(promoted.alsoSupplier()).isTrue();
        assertThat(promoted.supplierCode()).isEqualTo("F-DUAL-0001");

        Long partyRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM parties WHERE id = ? AND is_customer = true AND is_supplier = true",
                Long.class, created.id());
        assertThat(partyRows).isEqualTo(1L);

        Long arRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ar_balances WHERE party_id = ?",
                Long.class, created.id());
        assertThat(arRows).isEqualTo(1L);

        Long apRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ap_balances WHERE party_id = ?",
                Long.class, created.id());
        assertThat(apRows).isEqualTo(1L);
    }
}
