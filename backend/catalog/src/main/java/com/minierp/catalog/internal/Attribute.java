package com.minierp.catalog.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * A variant axis such as "Couleur" or "Taille". Its possible values live in
 * {@link AttributeValue}. A product selects a subset of values per attribute, and
 * the cartesian product of the selected values generates the product's variants.
 */
@Entity
@Table(name = "attributes",
        uniqueConstraints = @UniqueConstraint(name = "uk_attributes_tenant_name",
                columnNames = {"tenant_id", "name"}),
        indexes = @Index(name = "idx_attributes_tenant", columnList = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Attribute extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
