package com.minierp.tenant.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByCode(String code);
    boolean existsByCode(String code);
    Page<Organization> findAllByStatus(OrganizationStatus status, Pageable pageable);

    // ── Console listing: hide the reserved platform organization ──
    Page<Organization> findAllByCodeNot(String code, Pageable pageable);
    Page<Organization> findAllByStatusAndCodeNot(OrganizationStatus status, String code, Pageable pageable);

    // ── Used by TenantExpiryJob (runs without tenant context; organizations has no RLS) ──
    List<Organization> findAllByStatusAndTrialEndsAtBefore(OrganizationStatus status, Instant cutoff);
    List<Organization> findAllByStatusAndPastDueSinceBefore(OrganizationStatus status, Instant cutoff);
    List<Organization> findAllByStatusAndTrialEndsAtBetween(OrganizationStatus status, Instant from, Instant to);
}
