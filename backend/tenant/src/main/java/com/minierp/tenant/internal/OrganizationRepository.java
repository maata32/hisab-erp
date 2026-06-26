package com.minierp.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrganizationRepository extends JpaRepository<Organization, UUID>,
        JpaSpecificationExecutor<Organization> {
    Optional<Organization> findByCode(String code);
    boolean existsByCode(String code);

    // ── Used by TenantExpiryJob (runs without tenant context; organizations has no RLS) ──
    List<Organization> findAllByStatusAndTrialEndsAtBefore(OrganizationStatus status, Instant cutoff);
    List<Organization> findAllByStatusAndPastDueSinceBefore(OrganizationStatus status, Instant cutoff);
    List<Organization> findAllByStatusAndTrialEndsAtBetween(OrganizationStatus status, Instant from, Instant to);
}
