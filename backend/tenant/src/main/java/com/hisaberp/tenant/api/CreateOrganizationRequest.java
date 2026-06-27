package com.hisaberp.tenant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateOrganizationRequest(
        @NotBlank @Size(min = 2, max = 50)
        @Pattern(regexp = "^[a-z0-9-]+$", message = "Code must be lowercase alphanumeric with dashes only")
        String code,
        @NotBlank @Size(min = 2, max = 200) String name,
        @NotBlank String type,
        @Size(max = 3) String currency,
        @Size(max = 10) String locale,
        @Size(max = 50) String timezone,
        @Size(max = 200) String email,
        @Size(max = 30) String phone,
        @Size(max = 500) String address,
        UUID subscriptionPlanId) {}
