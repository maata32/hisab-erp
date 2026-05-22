package com.minierp.lotexpiry.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "product_lots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_lots_tenant_product_number_wh",
                columnNames = {"tenant_id", "product_id", "lot_number", "warehouse_id"}),
        indexes = {
                @Index(name = "idx_product_lots_product", columnList = "product_id"),
                @Index(name = "idx_product_lots_expiration", columnList = "expiration_date"),
                @Index(name = "idx_product_lots_status", columnList = "status"),
                @Index(name = "idx_product_lots_warehouse", columnList = "warehouse_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductLot extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "product_variant_id", columnDefinition = "uuid")
    private UUID productVariantId;

    @Column(name = "lot_number", nullable = false, length = 100)
    private String lotNumber;

    @Column(name = "production_date")
    private LocalDate productionDate;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(name = "initial_quantity", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal initialQuantity = BigDecimal.ZERO;

    @Column(name = "quantity_remaining", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal quantityRemaining = BigDecimal.ZERO;

    @Column(name = "uom_id", nullable = false, columnDefinition = "uuid")
    private UUID uomId;

    @Column(name = "warehouse_id", nullable = false, columnDefinition = "uuid")
    private UUID warehouseId;

    @Column(name = "purchase_order_id", columnDefinition = "uuid")
    private UUID purchaseOrderId;

    @Column(name = "party_id", columnDefinition = "uuid")
    private UUID partyId;

    @Column(name = "purchase_unit_cost", precision = 19, scale = 6)
    private BigDecimal purchaseUnitCost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LotStatus status = LotStatus.ACTIVE;

    @Column(name = "blocked_reason", length = 500)
    private String blockedReason;

    @Column(length = 1000)
    private String notes;
}
