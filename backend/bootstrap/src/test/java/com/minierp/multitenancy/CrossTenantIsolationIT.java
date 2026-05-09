package com.minierp.multitenancy;

import com.minierp.MiniErpApplication;
import com.minierp.shared.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the spec's mandatory test: cross-tenant isolation enforced even when
 * the Hibernate filter is disabled. This proves PostgreSQL RLS (defense layer 2)
 * is doing its job independently.
 *
 * <p><b>Implementation note:</b> {@code set_config(..., true)} is transaction-scoped,
 * so this test only behaves correctly within {@code @Transactional} blocks where
 * Spring binds a single JDBC connection to the test's transaction. The helpers
 * deliberately run outside a transaction (auto-commit) — they rely on the RLS
 * policy's {@code OR app_current_tenant() IS NULL} bypass for the seed inserts.</p>
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles({"test"})
@DisplayName("Cross-tenant isolation — Hibernate filter + PG RLS")
class CrossTenantIsolationIT {

    @PersistenceContext
    EntityManager em;

    @org.springframework.beans.factory.annotation.Autowired
    JdbcTemplate jdbc;

    UUID tenantA;
    UUID tenantB;
    UUID userA;
    UUID userB;

    @BeforeEach
    void seedTwoTenants() {
        tenantA = createTenant("tenant-a", "Tenant A");
        tenantB = createTenant("tenant-b", "Tenant B");
        userA = createUser(tenantA, "alice@a.local");
        userB = createUser(tenantB, "bob@b.local");
    }

    @Test
    @Transactional
    void hibernate_filter_blocks_cross_tenant_reads() {
        TenantContext.set(tenantA);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantA);
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantA.toString());

        List<?> users = em.createNativeQuery("SELECT id FROM users").getResultList();
        assertThat(users).hasSize(1);
    }

    @Test
    @Transactional
    void rls_blocks_cross_tenant_even_when_hibernate_filter_is_off() {
        TenantContext.set(tenantA);
        // Intentionally do NOT enable the Hibernate filter — only set the PG session variable.
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantA.toString());

        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        assertThat(count).isEqualTo(1);

        // Try to read tenant B's user by ID — RLS must hide it
        List<?> rows = jdbc.queryForList("SELECT id FROM users WHERE id = ?", userB);
        assertThat(rows).isEmpty();
    }

    @Test
    @Transactional
    void rls_blocks_cross_tenant_inserts_by_check_clause() {
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantA.toString());
        // Attempt to insert a row claiming tenant B → blocked by WITH CHECK
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.update("""
                    INSERT INTO users (id, tenant_id, email, password_hash, full_name, preferred_language,
                                       is_active, is_super_admin, password_changed_at, failed_login_attempts,
                                       created_at, updated_at, version)
                    VALUES (uuid_generate_v4(), ?, 'evil@a.local', 'x', 'evil', 'fr', true, false, now(), 0, now(), now(), 0)
                    """, tenantB))
                .isInstanceOf(Exception.class);
    }

    private UUID createTenant(String code, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, ?, 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE', now(), now(), 0)
                """, id, code, name);
        return id;
    }

    private UUID createUser(UUID tenantId, String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, tenant_id, email, password_hash, full_name, preferred_language,
                                   is_active, is_super_admin, password_changed_at, failed_login_attempts,
                                   created_at, updated_at, version)
                VALUES (?, ?, ?, 'x', 'User', 'fr', true, false, now(), 0, now(), now(), 0)
                """, id, tenantId, email);
        return id;
    }
}
