package com.hisaberp;

import com.hisaberp.identity.security.PasswordHasher;
import com.hisaberp.shared.tenant.TenantContext;
import com.hisaberp.tenant.api.CreateOrganizationRequest;
import com.hisaberp.tenant.api.OrganizationApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dev-only seeder. Creates "demo" tenant + a TENANT_ADMIN user (admin@demo.local / Admin1234!)
 * + a SUPER_ADMIN platform user (root@hisaberp.local / Root12345!).
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
        ensurePhase1aData(demoOrgId);

        // Dedicated platform super-admin, homed in a reserved (hidden) organization.
        UUID platformOrgId = ensurePlatformOrg();
        ensurePlatformSuperAdmin(platformOrgId);

        log.info("Dev data seeded. Demo tenant code: 'demo'");
        log.info("  Tenant admin:    admin@demo.local / Admin1234!");
        log.info("  Cashier:         cashier@demo.local / Cash1234!");
        log.info("  Super admin:     root@hisaberp.local / Root12345! (tenant code 'demo')");
        log.info("  Platform admin:  superadmin@hisaberp.local / Super1234! (platform login, no tenant code)");
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
                "demo@hisaberp.local", null, null, null));
        return dto.id();
    }

    private void ensureTenantAdmin(UUID orgId) {
        upsertUser(orgId, "admin@demo.local", "Admin Demo", "Admin1234!", "TENANT_ADMIN", false);
        upsertUser(orgId, "cashier@demo.local", "Cashier Demo", "Cash1234!", "CASHIER", false);
    }

    private void ensureSuperAdmin(UUID orgId) {
        upsertUser(orgId, "root@hisaberp.local", "Platform Root", "Root12345!", "TENANT_ADMIN", true);
    }

    /** Reserved, hidden organization that homes platform super-admin accounts. Idempotent. */
    private UUID ensurePlatformOrg() {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE code = ?", Integer.class, OrganizationApi.PLATFORM_ORG_CODE);
        if (exists != null && exists > 0) {
            return jdbc.queryForObject(
                    "SELECT id FROM organizations WHERE code = ?", UUID.class, OrganizationApi.PLATFORM_ORG_CODE);
        }
        var dto = organizationApi.create(new CreateOrganizationRequest(
                OrganizationApi.PLATFORM_ORG_CODE, "Plateforme (système)", "MIXTE",
                "MRU", "fr", "Africa/Nouakchott",
                "platform@hisaberp.local", null, null, null));
        return dto.id();
    }

    /** Dedicated platform super-admin: is_super_admin, NO tenant role (no business permissions). */
    private void ensurePlatformSuperAdmin(UUID platformOrgId) {
        upsertUser(platformOrgId, "superadmin@hisaberp.local", "Platform Super Admin", "Super1234!", null, true);
    }

    private void upsertUser(UUID orgId, String email, String name, String password, String roleCode, boolean superAdmin) {
        try {
            TenantContext.set(orgId);
            // Pin the DB session tenant so the RLS WITH CHECK on `users` matches this org.
            // Without this, a tenant left set by earlier seeding (e.g. ensurePhase1aData)
            // leaks onto the pooled connection and rejects inserts for a different org
            // (e.g. the platform super-admin homed in the reserved platform org).
            jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, false)",
                    String.class, orgId.toString());
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

            // A platform super-admin carries no tenant role (roleCode == null): its authority
            // comes solely from is_super_admin. Otherwise wait for the role to exist (it is
            // seeded asynchronously by TenantRolesBootstrapper) and assign it.
            if (roleCode != null) {
                UUID roleId = waitForRole(orgId, roleCode);
                if (roleId != null) {
                    jdbc.update("INSERT INTO user_role (user_id, role_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                            userId, roleId);
                }
            }
            log.info("Seeded {} user '{}' in tenant {}", roleCode == null ? "SUPER_ADMIN(platform)" : roleCode, email, orgId);
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

    // ── Phase 1A: UoM / Catalog / Pricing / Inventory / POS ──────────────────

    private void ensurePhase1aData(UUID orgId) {
        jdbc.queryForObject("SELECT set_config('app.current_tenant',?,false)",
                String.class, orgId.toString());

        UUID uomCatId = ensureUomCategory(orgId, "COUNT", "Count");
        UUID pceId    = ensureUom(orgId, uomCatId, "PCE", "Piece");

        UUID catId  = ensureProductCategory(orgId, "GENERAL", "Général");
        UUID prod1  = ensureProduct(orgId, "DEMO-001", "Eau minérale 1.5L", pceId, catId);
        UUID prod2  = ensureProduct(orgId, "DEMO-002", "Pain de mie", pceId, catId);

        UUID tierId = ensurePriceTier(orgId, "RETAIL", "Tarif public");
        ensureProductPrice(orgId, prod1, pceId, tierId, new BigDecimal("50.00"));
        ensureProductPrice(orgId, prod2, pceId, tierId, new BigDecimal("25.00"));

        UUID whId = ensureWarehouse(orgId, "MAIN", "Entrepôt principal");
        ensureCashRegister(orgId, "REG-01", "Caisse 01", whId, tierId);

        log.info("Phase 1A dev data seeded (UoM, catalog, pricing, inventory, POS).");
    }

    private UUID ensureUomCategory(UUID orgId, String code, String name) {
        UUID id = queryId("SELECT id FROM uom_categories WHERE tenant_id = ? AND code = ?", orgId, code);
        if (id != null) return id;
        id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, now(), now(), 0)
                """, id, orgId, code, name);
        return id;
    }

    private UUID ensureUom(UUID orgId, UUID categoryId, String code, String name) {
        UUID id = queryId("SELECT id FROM uoms WHERE tenant_id = ? AND code = ?", orgId, code);
        if (id != null) return id;
        id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name,
                                  ratio_to_base, is_base, decimal_places,
                                  created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 1, true, 0, now(), now(), 0)
                """, id, orgId, categoryId, code, name);
        return id;
    }

    private UUID ensureProductCategory(UUID orgId, String code, String name) {
        UUID id = queryId("SELECT id FROM product_categories WHERE tenant_id = ? AND code = ?", orgId, code);
        if (id != null) return id;
        id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO product_categories (id, tenant_id, code, name, sort_order,
                                                is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 0, true, now(), now(), 0)
                """, id, orgId, code, name);
        return id;
    }

    private UUID ensureProduct(UUID orgId, String sku, String name, UUID baseUomId, UUID categoryId) {
        UUID id = queryId("SELECT id FROM products WHERE tenant_id = ? AND sku = ?", orgId, sku);
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO products (id, tenant_id, sku, name, category_id, base_uom_id,
                                          default_tax_rate, tracks_lots, tracks_serial,
                                          is_sellable, is_purchasable, is_active,
                                          created_at, updated_at, version)
                    VALUES (?, ?, ?, ?, ?, ?, 0.00, false, false, true, true, true, now(), now(), 0)
                    """, id, orgId, sku, name, categoryId, baseUomId);
        }
        // Every product needs at least one variant (variant = SKU); prices/stock/lines key off it.
        ensureDefaultVariant(orgId, id, sku);
        return id;
    }

    private UUID ensureDefaultVariant(UUID orgId, UUID productId, String sku) {
        UUID id = queryId("""
                SELECT id FROM product_variants
                WHERE tenant_id = ? AND product_id = ? AND is_default = true
                """, orgId, productId);
        if (id != null) return id;
        id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default,
                                              is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, true, now(), now(), 0)
                """, id, orgId, productId, sku);
        return id;
    }

    private UUID ensurePriceTier(UUID orgId, String code, String name) {
        UUID id = queryId("SELECT id FROM price_tiers WHERE tenant_id = ? AND code = ?", orgId, code);
        if (id != null) return id;
        id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO price_tiers (id, tenant_id, code, name, is_default, is_active,
                                         created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, true, now(), now(), 0)
                """, id, orgId, code, name);
        return id;
    }

    private void ensureProductPrice(UUID orgId, UUID productId, UUID uomId, UUID tierId, BigDecimal amount) {
        Integer n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM product_prices
                WHERE tenant_id = ? AND product_id = ? AND uom_id = ?
                  AND price_tier_id = ? AND valid_from = '2000-01-01'
                """, Integer.class, orgId, productId, uomId, tierId);
        if (n != null && n > 0) return;
        UUID variantId = queryId("""
                SELECT id FROM product_variants
                WHERE tenant_id = ? AND product_id = ? AND is_default = true
                """, orgId, productId);
        jdbc.update("""
                INSERT INTO product_prices (id, tenant_id, product_id, variant_id, uom_id, price_tier_id,
                                            amount, currency, tax_inclusive, valid_from,
                                            created_at, updated_at, version)
                VALUES (uuid_generate_v4(), ?, ?, ?, ?, ?, ?, 'MRU', false, '2000-01-01', now(), now(), 0)
                """, orgId, productId, variantId, uomId, tierId, amount);
    }

    private UUID ensureWarehouse(UUID orgId, String code, String name) {
        UUID id = queryId("SELECT id FROM warehouses WHERE tenant_id = ? AND code = ?", orgId, code);
        if (id != null) return id;
        id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouses (id, tenant_id, code, name, is_default, is_active,
                                        created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, true, now(), now(), 0)
                """, id, orgId, code, name);
        return id;
    }

    private UUID ensureCashRegister(UUID orgId, String code, String name, UUID warehouseId, UUID priceTierId) {
        UUID id = queryId("SELECT id FROM cash_registers WHERE tenant_id = ? AND code = ?", orgId, code);
        if (id != null) return id;
        id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cash_registers (id, tenant_id, code, name, warehouse_id,
                                            default_price_tier_id, receipt_width_mm, is_active,
                                            created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 80, true, now(), now(), 0)
                """, id, orgId, code, name, warehouseId, priceTierId);
        return id;
    }

    private UUID queryId(String sql, Object... args) {
        try {
            return jdbc.queryForObject(sql, UUID.class, args);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }
}
