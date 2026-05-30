package com.minierp.allocation.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Row in the unified {@code allocations} table linking a positive-side item to
 * a negative-side item for one party. Phase 1 only persists rows — writes go
 * through Phase 2 once existing flows are refactored to delegate through
 * {@link com.minierp.allocation.api.AllocationEngine}.
 */
@Entity
@Table(name = "allocations",
        indexes = {
                @Index(name = "idx_allocations_party", columnList = "party_id"),
                @Index(name = "idx_allocations_positive", columnList = "positive_type,positive_id"),
                @Index(name = "idx_allocations_negative", columnList = "negative_type,negative_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Allocation extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "party_id", nullable = false, columnDefinition = "uuid")
    private UUID partyId;

    @Column(name = "positive_type", nullable = false, length = 30)
    private String positiveType;

    @Column(name = "positive_id", nullable = false, columnDefinition = "uuid")
    private UUID positiveId;

    @Column(name = "negative_type", nullable = false, length = 30)
    private String negativeType;

    @Column(name = "negative_id", nullable = false, columnDefinition = "uuid")
    private UUID negativeId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "allocated_at", nullable = false)
    @Builder.Default
    private Instant allocatedAt = Instant.now();

    @Column(length = 500)
    private String notes;
}
