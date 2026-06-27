package com.hisaberp.inventory.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * On-hand quantity for one (warehouse, variant) pair, expressed in the product's base UoM.
 * The variant is the real SKU; {@code product_id} is kept denormalized so product-level
 * rollups (e.g. the stock breakdown) need no variant lookup.
 * {@code averageCost} is the CMP (coût moyen pondéré) per base unit.
 */
@Entity
@Table(name = "stocks",
        uniqueConstraints = @UniqueConstraint(name = "uk_stocks_warehouse_variant",
                columnNames = {"warehouse_id", "variant_id"}),
        indexes = {
                @Index(name = "idx_stocks_variant", columnList = "variant_id"),
                @Index(name = "idx_stocks_product", columnList = "product_id"),
                @Index(name = "idx_stocks_warehouse", columnList = "warehouse_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Stock extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "warehouse_id", columnDefinition = "uuid", nullable = false)
    private UUID warehouseId;

    @Column(name = "variant_id", columnDefinition = "uuid", nullable = false)
    private UUID variantId;

    /** Denormalized parent product of {@link #variantId} for product-level rollups. */
    @Column(name = "product_id", columnDefinition = "uuid", nullable = false)
    private UUID productId;

    @Column(name = "qty_on_hand", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal qtyOnHand = BigDecimal.ZERO;

    @Column(name = "qty_reserved", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal qtyReserved = BigDecimal.ZERO;

    @Column(name = "average_cost", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal averageCost = BigDecimal.ZERO;
}
