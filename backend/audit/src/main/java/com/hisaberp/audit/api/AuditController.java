package com.hisaberp.audit.api;

import com.hisaberp.audit.internal.AuditQueryService;
import com.hisaberp.shared.security.CurrentUserHolder;
import com.hisaberp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Read-only access to the immutable audit trail")
public class AuditController {

    private final AuditQueryService service;

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    @Operation(summary = "List audit events for the current tenant in a time window")
    public PageResponse<AuditDto> list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID tenantId = CurrentUserHolder.require().tenantId();
        Instant f = from != null ? from : Instant.now().minus(7, ChronoUnit.DAYS);
        Instant t = to != null ? to : Instant.now();
        return service.list(tenantId, f, t, PageRequest.of(page, Math.min(size, 200)));
    }

    @GetMapping("/entity/{type}/{id}")
    @PreAuthorize("hasAuthority('audit:read')")
    @Operation(summary = "List audit events for one entity")
    public PageResponse<AuditDto> listByEntity(
            @PathVariable("type") String entityType,
            @PathVariable("id") String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID tenantId = CurrentUserHolder.require().tenantId();
        return service.listByEntity(tenantId, entityType, entityId, PageRequest.of(page, Math.min(size, 200)));
    }

    public record AuditDto(
            UUID id, UUID tenantId, UUID actorUserId, String action,
            String entityType, String entityId,
            java.util.Map<String, Object> oldValue, java.util.Map<String, Object> newValue,
            String ipAddress, String userAgent, Instant occurredAt) {}
}
