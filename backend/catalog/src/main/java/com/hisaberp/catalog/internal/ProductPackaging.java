package com.hisaberp.catalog.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A packaging UoM for a Product. The product's base UoM does not need a packaging row;
 * additional packaging units (carton, pack, dozen, etc.) live here. The {@code factor}
 * is the number of base-units in one packaging-unit (e.g. 6 base-units per "pack of 6").
 */
@Entity
@Table(name = "product_packagings",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_packagings_product_uom",
                columnNames = {"product_id", "uom_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class ProductPackaging extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "product_id", columnDefinition = "uuid", nullable = false)
    private UUID productId;

    @Column(name = "uom_id", columnDefinition = "uuid", nullable = false)
    private UUID uomId;

    @Column(name = "factor", precision = 19, scale = 6, nullable = false)
    private BigDecimal factor;

    @Column(name = "barcode", length = 64)
    private String barcode;

    @Column(name = "is_default_sale", nullable = false)
    @Builder.Default
    private boolean defaultSale = false;

    @Column(name = "is_default_purchase", nullable = false)
    @Builder.Default
    private boolean defaultPurchase = false;

    /** Whether the product can also be counted in stock under this packaging UoM. */
    @Column(name = "is_stock_uom", nullable = false)
    @Builder.Default
    private boolean stockUom = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
