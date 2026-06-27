package com.hisaberp.identity.internal;

import com.hisaberp.shared.tenant.TenantContext;
import com.hisaberp.tenant.events.TenantApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * When a SUPER_ADMIN approves a tenant, promote the registration's admin user to
 * TENANT_ADMIN. By approval time the default roles (seeded async on
 * OrganizationCreatedEvent) already exist. Listens asynchronously after commit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TenantApprovalListener {

    private static final String TENANT_ADMIN = "TENANT_ADMIN";

    private final UserRepository users;
    private final RoleRepository roles;

    @ApplicationModuleListener
    public void onTenantApproved(TenantApprovedEvent event) {
        if (event.adminUserId() == null) return;
        try {
            TenantContext.set(event.organizationId());
            users.findById(event.adminUserId()).ifPresent(user ->
                    roles.findByCodeAndTenantId(TENANT_ADMIN, event.organizationId()).ifPresentOrElse(
                            role -> {
                                user.getRoles().add(role);
                                users.save(user);
                                log.info("Granted TENANT_ADMIN to user {} for approved tenant {}",
                                        user.getId(), event.tenantCode());
                            },
                            () -> log.warn("TENANT_ADMIN role missing for tenant {} — admin not promoted",
                                    event.tenantCode())));
        } finally {
            TenantContext.clear();
        }
    }
}
