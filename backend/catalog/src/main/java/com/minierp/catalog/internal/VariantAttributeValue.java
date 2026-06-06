package com.minierp.catalog.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Source-of-truth link between a {@link ProductVariant} and one {@link AttributeValue}.
 * The full set for a variant defines its attribute combination (e.g. Rouge + M). The
 * variant's {@code attributes} jsonb is a denormalized display cache of these rows.
 */
@Entity
@Table(name = "variant_attribute_values",
        uniqueConstraints = @UniqueConstraint(name = "uk_vav_variant_value",
                columnNames = {"variant_id", "attribute_value_id"}),
        indexes = @Index(name = "idx_vav_variant", columnList = "variant_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class VariantAttributeValue extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "variant_id", columnDefinition = "uuid", nullable = false)
    private UUID variantId;

    @Column(name = "attribute_value_id", columnDefinition = "uuid", nullable = false)
    private UUID attributeValueId;
}
