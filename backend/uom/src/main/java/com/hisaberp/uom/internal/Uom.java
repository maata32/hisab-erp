package com.hisaberp.uom.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A unit-of-measure within a {@link UomCategory}. Conversion within a category is by
 * {@code ratioToBase}: amount_in_base = amount × ratioToBase. The base unit of each category
 * has ratioToBase = 1. Cross-category conversion is forbidden by {@code UomService}.
 */
@Entity
@Table(name = "uoms",
        uniqueConstraints = @UniqueConstraint(name = "uk_uoms_tenant_code", columnNames = {"tenant_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Uom extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "category_id", columnDefinition = "uuid", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "ratio_to_base", nullable = false, precision = 19, scale = 6)
    private BigDecimal ratioToBase;

    @Column(name = "is_base", nullable = false)
    @Builder.Default
    private boolean isBase = false;

    @Column(name = "decimal_places", nullable = false)
    @Builder.Default
    private int decimalPlaces = 3;
}
