package com.hisaberp.tenant.internal;

import com.hisaberp.shared.security.CurrentUserHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Used by @PreAuthorize SpEL expressions to check the caller is acting on their own tenant.
 * Bean name "tenantSecurity" is intentional and referenced from controller annotations.
 */
@Component("tenantSecurity")
public class TenantSecurity {

    public boolean isSelf(UUID organizationId) {
        return CurrentUserHolder.tryGet()
                .map(u -> organizationId != null && organizationId.equals(u.tenantId()))
                .orElse(false);
    }
}
