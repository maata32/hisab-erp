package com.hisaberp;

import com.hisaberp.identity.security.PasswordHasher;
import com.hisaberp.shared.tenant.TenantContext;
import com.hisaberp.tenant.api.CreateOrganizationRequest;
import com.hisaberp.tenant.api.OrganizationApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Production first-run bootstrap for the platform super-admin.
 *
 * <p>Prod ships no seed data ({@link DevDataSeeder} is {@code @Profile("dev")}), so without this
 * a freshly deployed system has no account to sign in with. This runner is OPT-IN and IDEMPOTENT:
 * <ul>
 *   <li>It does nothing unless both {@code BOOTSTRAP_ADMIN_EMAIL} and
 *       {@code BOOTSTRAP_ADMIN_PASSWORD} are provided (env vars, wired in the prod compose).</li>
 *   <li>It does nothing if a platform super-admin already exists — safe to keep across redeploys.</li>
 * </ul>
 *
 * <p>It ensures the reserved platform organization ({@link OrganizationApi#PLATFORM_ORG_CODE})
 * exists and creates a single super-admin homed there ({@code is_super_admin = true}, no tenant
 * role). The password is hashed with the app's Argon2id encoder. Sign in via
 * {@code POST /api/v1/auth/platform-login}.
 *
 * <p>Operational guidance: set the two env vars for the FIRST deploy only, then blank them and
 * rotate the password from the console.
 */
@Component
@Profile("prod")
@Slf4j
public class ProdAdminBootstrap implements ApplicationRunner {

    private final OrganizationApi organizationApi;
    private final JdbcTemplate jdbc;
    private final PasswordHasher hasher;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public ProdAdminBootstrap(
            OrganizationApi organizationApi,
            JdbcTemplate jdbc,
            PasswordHasher hasher,
            @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String adminEmail,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String adminPassword,
            @Value("${BOOTSTRAP_ADMIN_NAME:Platform Super Admin}") String adminName) {
        this.organizationApi = organizationApi;
        this.jdbc = jdbc;
        this.hasher = hasher;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminName = adminName;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = adminEmail == null ? "" : adminEmail.trim();
        if (email.isEmpty() || adminPassword == null || adminPassword.isBlank()) {
            log.info("Prod admin bootstrap skipped: BOOTSTRAP_ADMIN_EMAIL/PASSWORD not set.");
            return;
        }

        Integer superAdmins = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE is_super_admin = true", Integer.class);
        if (superAdmins != null && superAdmins > 0) {
            log.info("Prod admin bootstrap skipped: a platform super-admin already exists.");
            return;
        }

        if (adminPassword.length() < 12) {
            log.warn("BOOTSTRAP_ADMIN_PASSWORD is shorter than 12 chars — use a stronger secret.");
        }

        UUID platformOrgId = ensurePlatformOrg();
        createPlatformSuperAdmin(platformOrgId, email);
        log.info("Prod admin bootstrap: created platform super-admin '{}'. Sign in via "
                + "POST /api/v1/auth/platform-login, then unset BOOTSTRAP_ADMIN_* and rotate the password.", email);
    }

    /** Reserved, hidden organization that homes platform super-admin accounts. Idempotent. */
    private UUID ensurePlatformOrg() {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE code = ?", Integer.class, OrganizationApi.PLATFORM_ORG_CODE);
        if (exists != null && exists > 0) {
            return jdbc.queryForObject(
                    "SELECT id FROM organizations WHERE code = ?", UUID.class, OrganizationApi.PLATFORM_ORG_CODE);
        }
        return organizationApi.create(new CreateOrganizationRequest(
                OrganizationApi.PLATFORM_ORG_CODE, "Plateforme (système)", "MIXTE",
                "MRU", "fr", "Africa/Nouakchott",
                "platform@hisaberp.local", null, null, null)).id();
    }

    /** Dedicated platform super-admin: is_super_admin, NO tenant role (no business permissions). */
    private void createPlatformSuperAdmin(UUID orgId, String email) {
        try {
            TenantContext.set(orgId);
            // Pin the DB session tenant so the RLS WITH CHECK on `users` accepts the insert
            // (the runtime role is NOSUPERUSER/NOBYPASSRLS).
            jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, false)",
                    String.class, orgId.toString());
            String hash = hasher.hash(adminPassword);
            jdbc.update("""
                    INSERT INTO users (id, tenant_id, email, password_hash, full_name, preferred_language,
                                       is_active, is_super_admin, password_changed_at, failed_login_attempts,
                                       created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, 'fr', true, true, now(), 0, now(), now(), 0)
                    """, UUID.randomUUID(), orgId, email, hash, adminName);
        } finally {
            TenantContext.clear();
        }
    }
}
