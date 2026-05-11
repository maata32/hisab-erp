package com.minierp.catalog.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_products_tenant_sku", columnNames = {"tenant_id", "sku"}),
                @UniqueConstraint(name = "uk_products_tenant_barcode", columnNames = {"tenant_id", "barcode"})
        },
        indexes = {
                @Index(name = "idx_products_category", columnList = "category_id"),
                @Index(name = "idx_products_brand", columnList = "brand_id"),
                @Index(name = "idx_products_active", columnList = "is_active")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Product extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(length = 64)
    private String barcode;

    @Column(nullable = false, length = 250)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "category_id", columnDefinition = "uuid")
    private UUID categoryId;

    @Column(name = "brand_id", columnDefinition = "uuid")
    private UUID brandId;

    @Column(name = "base_uom_id", columnDefinition = "uuid", nullable = false)
    private UUID baseUomId;

    @Column(name = "default_tax_rate", precision = 5, scale = 4, nullable = false)
    @Builder.Default
    private BigDecimal defaultTaxRate = new BigDecimal("0.16");

    @Column(name = "tracks_lots", nullable = false)
    @Builder.Default
    private boolean tracksLots = false;

    @Column(name = "track_expiry", nullable = false)
    @Builder.Default
    private boolean trackExpiry = false;

    @Column(name = "shelf_life_days")
    private Integer shelfLifeDays;

    @Column(name = "tracks_serial", nullable = false)
    @Builder.Default
    private boolean tracksSerial = false;

    @Column(name = "is_sellable", nullable = false)
    @Builder.Default
    private boolean sellable = true;

    @Column(name = "is_purchasable", nullable = false)
    @Builder.Default
    private boolean purchasable = true;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "weight_grams", precision = 12, scale = 3)
    private BigDecimal weightGrams;
}
