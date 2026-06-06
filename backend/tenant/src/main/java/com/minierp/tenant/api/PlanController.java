package com.minierp.tenant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
@RequiredArgsConstructor
@Tag(name = "Subscription plans", description = "Public catalogue of subscription plans")
public class PlanController {

    private final TenantLookup tenantLookup;

    @GetMapping
    @Operation(summary = "List active subscription plans (public — used by the registration form)")
    public List<PlanView> list() {
        return tenantLookup.listActivePlans();
    }
}
