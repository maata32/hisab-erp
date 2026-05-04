package com.minierp.tenant.api;

import com.minierp.tenant.internal.OrganizationService;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Tenant management — SUPER_ADMIN only")
public class OrganizationController {

    private final OrganizationService service;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all organizations (super-admin)")
    public PageResponse<OrganizationDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(PageRequest.of(page, Math.min(size, 100)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create a new tenant organization")
    public OrganizationDto create(@Valid @RequestBody CreateOrganizationRequest req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or @tenantSecurity.isSelf(#id)")
    @Operation(summary = "Get an organization")
    public OrganizationDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN') or (@tenantSecurity.isSelf(#id) and hasRole('TENANT_ADMIN'))")
    @Operation(summary = "Update an organization (limited fields for tenant admin)")
    public OrganizationDto update(@PathVariable UUID id, @Valid @RequestBody UpdateOrganizationRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Suspend an organization")
    public ResponseEntity<Void> suspend(@PathVariable UUID id, @RequestBody(required = false) SuspendRequest req) {
        service.suspend(id, req == null ? null : req.reason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Reactivate a suspended organization")
    public ResponseEntity<Void> reactivate(@PathVariable UUID id) {
        service.reactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Return the calling user's own organization")
    public OrganizationDto me() {
        return service.get(CurrentUserHolder.require().tenantId());
    }

    public record OrganizationDto(
            UUID id, String code, String name, String type,
            String status, String currency, String locale, String timezone,
            String email, String phone, String address) {}

    public record CreateOrganizationRequest(
            @NotBlank @Size(min = 2, max = 50) @Pattern(regexp = "^[a-z0-9-]+$",
                    message = "Code must be lowercase alphanumeric with dashes only") String code,
            @NotBlank @Size(min = 2, max = 200) String name,
            @NotBlank String type,
            @Size(max = 3) String currency,
            @Size(max = 10) String locale,
            @Size(max = 50) String timezone,
            @Size(max = 200) String email,
            @Size(max = 30) String phone,
            @Size(max = 500) String address,
            UUID subscriptionPlanId) {}

    public record UpdateOrganizationRequest(
            @Size(max = 200) String name,
            @Size(max = 200) String email,
            @Size(max = 30) String phone,
            @Size(max = 500) String address,
            @Size(max = 500) String logoUrl,
            @Size(max = 10) String locale,
            @Size(max = 50) String timezone) {}

    public record SuspendRequest(@Size(max = 500) String reason) {}
}
