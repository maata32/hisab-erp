package com.hisaberp.sales.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface DocumentNumberSequenceRepository extends JpaRepository<DocumentNumberSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DocumentNumberSequence s WHERE s.tenantId = :tenantId AND s.documentType = :type AND s.year = :year")
    Optional<DocumentNumberSequence> lockByTenantTypeYear(UUID tenantId, DocumentType type, int year);

    // Ensures the row exists before the PESSIMISTIC_WRITE lock; safe under concurrent first-use.
    @Modifying
    @Query(value = """
            INSERT INTO document_number_sequences
                (id, tenant_id, document_type, year, counter, prefix, created_at, updated_at, version)
            VALUES
                (gen_random_uuid(), :tenantId, :type, :year, 0, :prefix, now(), now(), 0)
            ON CONFLICT (tenant_id, document_type, year) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(@Param("tenantId") UUID tenantId, @Param("type") String type,
                        @Param("year") int year, @Param("prefix") String prefix);
}
