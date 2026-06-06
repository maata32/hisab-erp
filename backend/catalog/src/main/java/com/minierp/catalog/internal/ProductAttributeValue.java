package com.minierp.catalog.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Join row marking that {@code attributeValueId} is one of the values enabled for
 * {@code productId}. The set of these rows, grouped by attribute, is the input to
 * variant generation (cartesian product).
 */
@Entity
@Table(name = "product_attribute_values",
        uniqueConstraints = @UniqueConstraint(name = "uk_pav_product_value",
                columnNames = {"product_id", "attribute_value_id"}),
        indexes = @Index(name = "idx_pav_product", columnList = "product_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ProductAttributeValue extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "product_id", columnDefinition = "uuid", nullable = false)
    private UUID productId;

    @Column(name = "attribute_value_id", columnDefinition = "uuid", nullable = false)
    private UUID attributeValueId;
}
