package com.minierp.sales.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface DocumentNumberSequenceRepository extends JpaRepository<DocumentNumberSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentNumberSequence s WHERE s.tenantId = :tenantId AND s.documentType = :type AND s.year = :year")
    Optional<DocumentNumberSequence> lockByTenantTypeYear(UUID tenantId, DocumentType type, int year);
}
