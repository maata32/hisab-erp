package com.minierp.tenant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Self-service registration payload for a new tenant organization. Creates the
 * organization in PENDING status (see {@link OrganizationApi#register}). The
 * caller (identity module) creates the admin user separately and then calls
 * {@link OrganizationApi#setPrimaryAdmin}.
 */
public record RegisterOrganizationRequest(
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
        @Size(max = 50) String planCode) {}
