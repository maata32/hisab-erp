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
                    "inventory:transfer", "inventory:count",
                    "lot:read", "lot:write",
                    "uom:read", "uom:create",
                    "pos:operate", "pos:open_session", "pos:close_session", "pos:cancel_sale", "pos:apply_discount",
                    "sale:read", "sale:create",
                    "sales:read", "sales:write",
                    "invoice:read", "invoice:issue", "invoice:cancel",
                    "customer:read", "customer:create", "customer:update", "customer:write",
                    "payment:read", "payment:create", "payment:cancel", "payment:write",
                    "credit:grant", "credit:withdraw",
                    "delivery:read", "delivery:plan", "delivery:execute", "delivery:write",
                    "expense:read", "expense:create", "expense:approve", "expense:write",
                    "treasury:read", "treasury:manage",
                    "employee:read", "employee:manage", "salary:manage",
                    "report:view", "report:export", "reporting:read")),
            Map.entry("MANAGER", List.of(
                    "user:read",
                    "product:read", "product:create", "product:update", "price:update",
                    "stock:read", "stock:adjust",
                    "inventory:transfer", "inventory:count",
                    "lot:read", "lot:write",
                    "uom:read", "uom:create",
                    "pos:operate", "pos:cancel_sale", "pos:apply_discount",
                    "sale:read", "sale:create",
                    "sales:read", "sales:write",
                    "invoice:read", "invoice:issue", "invoice:cancel",
                    "customer:read", "customer:create", "customer:update", "customer:write",
                    "payment:read", "payment:create", "payment:cancel", "payment:write",
                    "credit:grant",
                    "delivery:read", "delivery:plan", "delivery:write",
                    "expense:read", "expense:create", "expense:approve", "expense:write",
                    "treasury:read", "treasury:manage",
                    "report:view", "report:export", "reporting:read",
                    "audit:read")),
            Map.entry("ACCOUNTANT", List.of(
                    "invoice:read",
                    "sales:read",
                    "payment:read",
                    "expense:read",
                    "report:view", "report:export", "reporting:read",
                    "audit:read",
                    "customer:read")),
            Map.entry("PAYMENT_OFFICER", List.of(
                    "invoice:read",
                    "sales:read",
                    "customer:read", "customer:write",
                    "payment:read", "payment:create", "payment:cancel", "payment:write",
                    "credit:grant", "credit:withdraw",
                    "report:view", "reporting:read")),
            Map.entry("CASHIER", List.of(
                    "product:read",
                    "uom:read",
                    "stock:read",
                    "lot:read",
                    "pos:operate", "pos:open_session", "pos:close_session", "pos:cancel_sale",
                    "sale:read", "sale:create",
                    "sales:read", "sales:write",
                    "customer:read", "customer:create", "customer:write",
                    "payment:read", "payment:create", "payment:write")),
            Map.entry("STOCK_KEEPER", List.of(
                    "product:read",
                    "uom:read",
                    "stock:read", "stock:adjust", "warehouse:manage",
                    "inventory:transfer", "inventory:count",
                    "lot:read", "lot:write",
                    "delivery:read", "delivery:plan", "delivery:execute", "delivery:write")),
            Map.entry("HR_MANAGER", List.of(
                    "employee:read", "employee:manage", "salary:manage",
                    "report:view")),
            Map.entry("DRIVER", List.of(
                    "delivery:read", "delivery:execute",
                    "customer:read")),
            Map.entry("VIEWER", List.of(
                    "product:read", "stock:read", "sale:read", "sales:read", "invoice:read",
                    "customer:read", "payment:read", "delivery:read", "expense:read",
                    "uom:read", "lot:read",
                    "report:view", "reporting:read"))
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
