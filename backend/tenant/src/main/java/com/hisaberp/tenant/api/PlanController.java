package com.hisaberp.tenant.api;

import com.hisaberp.tenant.internal.SubscriptionPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Subscription plans", description = "Subscription plans (public catalogue + super-admin CRUD)")
public class PlanController {

    private final TenantLookup tenantLookup;
    private final SubscriptionPlanService planService;

    @GetMapping
    @Operation(summary = "List active subscription plans (public — used by the registration form)")
    public List<PlanView> list() {
        return tenantLookup.listActivePlans();
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "List all plans incl. inactive (super-admin)")
    public List<SubscriptionPlanDto> listAll() {
        return planService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SubscriptionPlanDto create(@Valid @RequestBody SubscriptionPlanDto.CreateRequest req) {
        return planService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public SubscriptionPlanDto update(@PathVariable UUID id, @Valid @RequestBody SubscriptionPlanDto.UpdateRequest req) {
        return planService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void delete(@PathVariable UUID id) {
        planService.delete(id);
    }
}
