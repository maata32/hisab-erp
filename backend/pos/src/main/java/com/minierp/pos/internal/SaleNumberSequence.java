package com.minierp.pos.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Year;
import java.util.UUID;

/**
 * Per-tenant sale number sequence. One row per (tenant, year). Allocation uses
 * a row-lock so concurrent sales receive distinct numbers.
 */
@Entity
@Table(name = "sale_number_sequences",
        uniqueConstraints = @UniqueConstraint(name = "uk_sale_number_sequences_tenant_year",
                columnNames = {"tenant_id", "year"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SaleNumberSequence extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    @Builder.Default
    private long counter = 0L;

    static SaleNumberSequence forYear(int year) {
        return SaleNumberSequence.builder().year(year).counter(0L).build();
    }

    static SaleNumberSequence currentYear() {
        return forYear(Year.now().getValue());
    }
}
