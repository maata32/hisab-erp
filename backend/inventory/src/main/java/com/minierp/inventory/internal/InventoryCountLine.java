package com.minierp.inventory.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "inventory_count_lines",
        indexes = @Index(name = "idx_icl_count", columnList = "count_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class InventoryCountLine extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "count_id", nullable = false, columnDefinition = "uuid")
    private InventoryCount count;

    @Column(name = "variant_id", nullable = false, columnDefinition = "uuid")
    private UUID variantId;

    @Column(name = "lot_id", columnDefinition = "uuid")
    private UUID lotId;

    @Column(name = "uom_id", nullable = false, columnDefinition = "uuid")
    private UUID uomId;

    @Column(name = "theoretical_qty", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal theoreticalQty = BigDecimal.ZERO;

    @Column(name = "counted_qty", precision = 19, scale = 6)
    private BigDecimal countedQty;

    @Column(precision = 19, scale = 6)
    private BigDecimal discrepancy;

    @Column(name = "unit_cost", precision = 19, scale = 6)
    private BigDecimal unitCost;

    @Column(length = 500)
    private String notes;
}
