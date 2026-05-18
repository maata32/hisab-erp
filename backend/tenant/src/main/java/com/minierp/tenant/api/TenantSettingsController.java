package com.minierp.tenant.api;

import com.minierp.tenant.internal.TenantSettingsService;
import com.minierp.tenant.internal.TenantSettingsService.UpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Tenant Settings", description = "Read and update the calling tenant's settings")
public class TenantSettingsController {

    private final TenantSettingsService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public TenantSettingsDto get() {
        return service.getForCurrentTenant();
    }

    @PutMapping
    @PreAuthorize("hasAuthority('tenant_settings:update')")
    public TenantSettingsDto update(@RequestBody UpdateRequest req) {
        return service.update(req);
    }
}
