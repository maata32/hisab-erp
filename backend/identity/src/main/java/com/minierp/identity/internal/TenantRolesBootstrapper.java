package com.minierp.identity.internal;

import com.minierp.shared.tenant.TenantContext;
import com.minierp.tenant.events.OrganizationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seeds the 9 default roles for a freshly created tenant. Listens asynchronously
 * to OrganizationCreatedEvent so org creation transactions stay short.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TenantRolesBootstrapper {

    private final RoleRepository roles;
    private final PermissionRepository permissions;

    /**
     * Permission grants per role code. Keep aligned with ADR-012 (DRIVER added).
     */
    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.ofEntries(
            Map.entry("TENANT_ADMIN", List.of(
                    "organization:read", "organization:update",
                    "tenant_settings:read", "tenant_settings:update",
                    "user:read", "user:create", "user:update", "user:delete",
                    "role:read", "role:assign", "role:create",
                    "audit:read",
                    "product:read", "product:create", "product:update", "product:delete", "price:update",
                    "stock:read", "stock:adjust", "warehouse:manage",
                    "pos:operate", "pos:open_session", "pos:close_session", "pos:cancel_sale", "pos:apply_discount",
                    "sale:read", "sale:create",
                    "invoice:read", "invoice:issue", "invoice:cancel",
                    "customer:read", "customer:create", "customer:update",
                    "payment:read", "payment:create", "payment:cancel",
                    "credit:grant", "credit:withdraw",
                    "delivery:read", "delivery:plan", "delivery:execute",
                    "expense:read", "expense:create", "expense:approve",
                    "employee:read", "employee:manage", "salary:manage",
                    "report:view", "report:export")),
            Map.entry("MANAGER", List.of(
                    "user:read",
                    "product:read", "product:create", "product:update", "price:update",
                    "stock:read", "stock:adjust",
                    "pos:operate", "pos:cancel_sale", "pos:apply_discount",
                    "sale:read", "sale:create",
                    "invoice:read", "invoice:issue", "invoice:cancel",
                    "customer:read", "customer:create", "customer:update",
                    "payment:read", "payment:create", "payment:cancel",
                    "credit:grant",
                    "delivery:read", "delivery:plan",
                    "expense:read", "expense:create", "expense:approve",
                    "report:view", "report:export",
                    "audit:read")),
            Map.entry("ACCOUNTANT", List.of(
                    "invoice:read",
                    "payment:read",
                    "expense:read",
                    "report:view", "report:export",
                    "audit:read",
                    "customer:read")),
            Map.entry("PAYMENT_OFFICER", List.of(
                    "invoice:read",
                    "customer:read",
                    "payment:read", "payment:create", "payment:cancel",
                    "credit:grant", "credit:withdraw",
                    "report:view")),
            Map.entry("CASHIER", List.of(
                    "product:read",
                    "stock:read",
                    "pos:operate", "pos:open_session", "pos:close_session",
                    "sale:read", "sale:create",
                    "customer:read", "customer:create",
                    "payment:read", "payment:create")),
            Map.entry("STOCK_KEEPER", List.of(
                    "product:read",
                    "stock:read", "stock:adjust", "warehouse:manage",
                    "delivery:read", "delivery:plan", "delivery:execute")),
            Map.entry("HR_MANAGER", List.of(
                    "employee:read", "employee:manage", "salary:manage",
                    "report:view")),
            Map.entry("DRIVER", List.of(
                    "delivery:read", "delivery:execute",
                    "customer:read")),
            Map.entry("VIEWER", List.of(
                    "product:read", "stock:read", "sale:read", "invoice:read",
                    "customer:read", "payment:read", "delivery:read", "expense:read",
                    "report:view"))
    );

    @ApplicationModuleListener
    public void onOrgCreated(OrganizationCreatedEvent event) {
        try {
            TenantContext.set(event.organizationId());
            ROLE_PERMISSIONS.forEach((code, permCodes) -> seedRole(code, permCodes));
            log.info("Seeded {} default roles for tenant {}", ROLE_PERMISSIONS.size(), event.code());
        } finally {
            TenantContext.clear();
        }
    }

    private void seedRole(String code, List<String> permCodes) {
        Role role = Role.builder()
                .code(code)
                .name(humanize(code))
                .description("System role: " + humanize(code))
                .system(true)
                .permissions(Set.copyOf(permissions.findAllByCodeIn(permCodes)))
                .build();
        roles.save(role);
    }

    private String humanize(String code) {
        StringBuilder sb = new StringBuilder();
        for (String part : code.split("_")) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
