package com.minierp.catalog.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "product_variants",
        indexes = @Index(name = "idx_product_variants_product", columnList = "product_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ProductVariant extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "product_id", columnDefinition = "uuid", nullable = false)
    private UUID productId;

    @Column(length = 64)
    private String sku;

    @Column(length = 64)
    private String barcode;

    /** JSON object of attribute key/value pairs, e.g. {"color":"red","size":"M"}. */
    @Column(columnDefinition = "jsonb")
    private String attributes;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
