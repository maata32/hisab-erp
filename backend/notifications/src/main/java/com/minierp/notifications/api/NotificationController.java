package com.minierp.notifications.api;

import com.minierp.notifications.internal.NotificationConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CDC §15.4 — read the system event catalog and CRUD the per-tenant config.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notification event catalog and per-tenant configuration")
public class NotificationController {

    private final NotificationConfigService service;

    @GetMapping("/events")
    @PreAuthorize("hasAuthority('tenant_settings:read')")
    public List<NotificationDto.EventDefinition> events() {
        return service.listEvents();
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('tenant_settings:read')")
    public List<NotificationDto.TenantConfig> config() {
        return service.listTenantConfig();
    }

    @PutMapping("/config/{eventCode}")
    @PreAuthorize("hasAuthority('tenant_settings:update')")
    public NotificationDto.TenantConfig putConfig(
            @PathVariable String eventCode,
            @Valid @RequestBody NotificationDto.TenantConfigRequest req) {
        return service.upsertConfig(eventCode, req.enabled(),
                req.channels(), req.recipients(),
                req.customRoles(), req.customUsers());
    }

    @PostMapping("/config/reset")
    @PreAuthorize("hasAuthority('tenant_settings:update')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset() {
        service.resetToDefaults();
    }
}
