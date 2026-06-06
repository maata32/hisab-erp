package com.minierp.pricing.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Price for a (variant, optional packaging UoM, price tier) combination, valid in
 * an optional time window. The variant is the real SKU; {@code product_id} is kept
 * denormalized for the product-level grid and the uniform-pricing fallback. Composite
 * uniqueness on (tenant_id, variant_id, uom_id, price_tier_id, valid_from) prevents
 * overlapping rows.
 */
@Entity
@Table(name = "product_prices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_prices_unique",
                columnNames = {"tenant_id", "variant_id", "uom_id", "price_tier_id", "valid_from"}),
        indexes = {
                @Index(name = "idx_product_prices_variant", columnList = "variant_id"),
                @Index(name = "idx_product_prices_product", columnList = "product_id"),
                @Index(name = "idx_product_prices_tier", columnList = "price_tier_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ProductPrice extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "variant_id", columnDefinition = "uuid", nullable = false)
    private UUID variantId;

    /** Denormalized parent product of {@link #variantId} (product-level grid & fallback). */
    @Column(name = "product_id", columnDefinition = "uuid", nullable = false)
    private UUID productId;

    /** UoM the price is expressed in. Null means "the product's base UoM". */
    @Column(name = "uom_id", columnDefinition = "uuid", nullable = false)
    private UUID uomId;

    @Column(name = "price_tier_id", columnDefinition = "uuid", nullable = false)
    private UUID priceTierId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MRU";

    @Column(name = "tax_inclusive", nullable = false)
    @Builder.Default
    private boolean taxInclusive = false;

    @Column(name = "valid_from", nullable = false)
    @Builder.Default
    private LocalDate validFrom = LocalDate.of(2000, 1, 1);

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "min_qty", precision = 19, scale = 6)
    private BigDecimal minQty;
}
