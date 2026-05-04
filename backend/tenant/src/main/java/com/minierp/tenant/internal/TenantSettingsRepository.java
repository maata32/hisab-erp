package com.minierp.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface TenantSettingsRepository extends JpaRepository<TenantSettings, UUID> {
    Optional<TenantSettings> findByOrganizationId(UUID organizationId);
}
