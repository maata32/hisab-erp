package com.minierp.inventory.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "stock_transfer_lines",
        indexes = @Index(name = "idx_stl_transfer", columnList = "transfer_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class StockTransferLine extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "transfer_id", nullable = false, columnDefinition = "uuid")
    private UUID transferId;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "lot_id", columnDefinition = "uuid")
    private UUID lotId;

    @Column(name = "uom_id", nullable = false, columnDefinition = "uuid")
    private UUID uomId;

    @Column(name = "quantity_requested", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityRequested;

    @Column(name = "quantity_transferred", nullable = false, precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal quantityTransferred = BigDecimal.ZERO;

    @Column(name = "unit_cost", precision = 19, scale = 6)
    private BigDecimal unitCost;

    @Column(length = 500)
    private String notes;
}
