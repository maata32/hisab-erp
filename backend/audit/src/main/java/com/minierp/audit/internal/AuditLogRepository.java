package com.minierp.audit.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByTenantIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID tenantId, Instant from, Instant to, Pageable pageable);

    Page<AuditLog> findByTenantIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
            UUID tenantId, String entityType, String entityId, Pageable pageable);
}
