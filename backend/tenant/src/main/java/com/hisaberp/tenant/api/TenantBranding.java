package com.hisaberp.tenant.api;

import java.util.UUID;

/**
 * Branding/identity fields used when rendering tenant-facing documents (PDFs).
 * Returned by {@link TenantLookup#findBrandingById(UUID)}.
 */
public record TenantBranding(
        UUID id,
        String name,
        String address,
        String phone,
        String email,
        String logoUrl) {}
