package com.minierp.delivery.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "delivery_lines",
        indexes = @Index(name = "idx_delivery_lines_delivery", columnList = "delivery_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class DeliveryLine extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "delivery_id", nullable = false, columnDefinition = "uuid")
    private UUID deliveryId;

    @Column(name = "order_line_id", columnDefinition = "uuid")
    private UUID orderLineId;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "uom_id", nullable = false, columnDefinition = "uuid")
    private UUID uomId;

    @Column(name = "quantity_ordered", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityOrdered;

    @Column(name = "quantity_delivered", nullable = false, precision = 19, scale = 6)
    @Builder.Default private BigDecimal quantityDelivered = BigDecimal.ZERO;

    @Column(name = "snapshot_name", length = 250)
    private String snapshotName;

    @Column(name = "snapshot_sku", length = 64)
    private String snapshotSku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DeliveryLineStatus status = DeliveryLineStatus.PENDING;
}
