package com.minierp.catalog.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * One possible value of an {@link Attribute}, e.g. "Rouge" for "Couleur" or "M" for
 * "Taille". {@code code} is a short token used when auto-suggesting variant SKUs.
 */
@Entity
@Table(name = "attribute_values",
        uniqueConstraints = @UniqueConstraint(name = "uk_attribute_values_attr_value",
                columnNames = {"attribute_id", "value"}),
        indexes = @Index(name = "idx_attribute_values_attribute", columnList = "attribute_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class AttributeValue extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "attribute_id", columnDefinition = "uuid", nullable = false)
    private UUID attributeId;

    @Column(nullable = false, length = 100)
    private String value;

    @Column(length = 32)
    private String code;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
