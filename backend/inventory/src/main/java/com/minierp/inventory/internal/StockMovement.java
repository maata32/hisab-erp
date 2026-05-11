package com.minierp.inventory.internal;

import com.minierp.inventory.api.StockMovementType;
import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable journal entry for a single stock change. Quantity is signed
 * (positive = inflow, negative = outflow) and expressed in base UoM.
 *
 * No setters are exposed — these rows are append-only. Database-side trigger
 * (mirroring the audit log pattern) enforces immutability.
 */
@Entity
@Table(name = "stock_movements",
        indexes = {
                @Index(name = "idx_stock_movements_warehouse_product",
                        columnList = "warehouse_id,product_id"),
                @Index(name = "idx_stock_movements_occurred", columnList = "occurred_at"),
                @Index(name = "idx_stock_movements_ref", columnList = "reference_type,reference_id")
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class StockMovement extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "warehouse_id", columnDefinition = "uuid", nullable = false)
    private UUID warehouseId;

    @Column(name = "product_id", columnDefinition = "uuid", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StockMovementType type;

    @Column(name = "qty_signed", nullable = false, precision = 19, scale = 6)
    private BigDecimal qtySigned;

    @Column(name = "unit_cost", precision = 19, scale = 6)
    private BigDecimal unitCost;

    @Column(name = "lot_id", columnDefinition = "uuid")
    private UUID lotId;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id", columnDefinition = "uuid")
    private UUID referenceId;

    @Column(name = "reference_number", length = 60)
    private String referenceNumber;

    @Column(length = 500)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;
}
