package com.minierp;

import com.minierp.identity.security.PasswordHasher;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.tenant.api.CreateOrganizationRequest;
import com.minierp.tenant.api.OrganizationApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Dev-only seeder. Creates "demo" tenant + a TENANT_ADMIN user (admin@demo.local / Admin1234!)
 * + a SUPER_ADMIN platform user (root@minierp.local / Root12345!).
 * Idempotent: skips if already present.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements ApplicationRunner {

    private final OrganizationApi organizationApi;
    private final JdbcTemplate jdbc;
    private final PasswordHasher hasher;

    @Override
    public void run(ApplicationArguments args) {
        // Each helper opens its own transaction so the org INSERT commits before
        // the user INSERT references it (and so the role-bootstrap listener fires).
        UUID demoOrgId = ensureDemoOrg();
        ensureTenantAdmin(demoOrgId);
        ensureSuperAdmin(demoOrgId);
        log.info("Dev data seeded. Demo tenant code: 'demo'");
        log.info("  Tenant admin: admin@demo.local / Admin1234!");
        log.info("  Super admin:  root@minierp.local / Root12345!");
    }

    private UUID ensureDemoOrg() {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE code = 'demo'", Integer.class);
        if (exists != null && exists > 0) {
            return jdbc.queryForObject("SELECT id FROM organizations WHERE code = 'demo'", UUID.class);
        }
        var dto = organizationApi.create(new CreateOrganizationRequest(
                "demo", "Demo Boutique", "BOUTIQUE",
                "MRU", "fr", "Africa/Nouakchott",
                "demo@minierp.local", null, null, null));
        return dto.id();
    }

    private void ensureTenantAdmin(UUID orgId) {
        upsertUser(orgId, "admin@demo.local", "Admin Demo", "Admin1234!", "TENANT_ADMIN", false);
    }

    private void ensureSuperAdmin(UUID orgId) {
        upsertUser(orgId, "root@minierp.local", "Platform Root", "Root12345!", "TENANT_ADMIN", true);
    }

    private void upsertUser(UUID orgId, String email, String name, String password, String roleCode, boolean superAdmin) {
        try {
            TenantContext.set(orgId);
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE tenant_id = ? AND email = ?",
                    Integer.class, orgId, email);
            if (exists != null && exists > 0) return;

            String hash = hasher.hash(password);
            UUID userId = UUID.randomUUID();
            jdbc.update("""
                INSERT INTO users (id, tenant_id, email, password_hash, full_name, preferred_language,
                                   is_active, is_super_admin, password_changed_at, failed_login_attempts,
                                   created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'fr', true, ?, now(), 0, now(), now(), 0)
                """, userId, orgId, email, hash, name, superAdmin);

            // Wait for the role to exist (it's seeded asynchronously by TenantRolesBootstrapper).
            UUID roleId = waitForRole(orgId, roleCode);
            if (roleId != null) {
                jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                        userId, roleId);
            }
            log.info("Seeded {} user '{}' in tenant {}", roleCode, email, orgId);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID waitForRole(UUID orgId, String code) {
        for (int i = 0; i < 50; i++) {
            try {
                return jdbc.queryForObject(
                        "SELECT id FROM roles WHERE tenant_id = ? AND code = ?",
                        UUID.class, orgId, code);
            } catch (org.springframework.dao.EmptyResultDataAccessException e) {
                try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        log.warn("Role {} not found for tenant {} after 5s", code, orgId);
        return null;
    }
}
