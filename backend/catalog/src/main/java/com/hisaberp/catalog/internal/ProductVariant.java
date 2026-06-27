package com.hisaberp.catalog.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** The actual stock-keeping unit code. Required and unique per tenant. */
    @Column(nullable = false, length = 64)
    private String sku;

    @Column(length = 64)
    private String barcode;

    /**
     * Denormalized display cache of the variant's attribute combination, e.g.
     * {@code {"Couleur":"Rouge","Taille":"M"}}. The source of truth is the set of
     * {@link VariantAttributeValue} rows; this is rebuilt on every generation.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String attributes;

    /**
     * True for the implicit single variant of an attribute-less product. Such products
     * always keep exactly one default variant so stock/lines always reference a variant.
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultVariant = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
