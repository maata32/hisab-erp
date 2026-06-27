package com.hisaberp.purchase.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "goods_receipt_lines",
        indexes = @Index(name = "idx_goods_receipt_lines_receipt", columnList = "goods_receipt_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class GoodsReceiptLine extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "goods_receipt_id", nullable = false, columnDefinition = "uuid")
    private UUID goodsReceiptId;

    @Column(name = "variant_id", columnDefinition = "uuid")
    private UUID variantId;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "lot_id", columnDefinition = "uuid")
    private UUID lotId;

    @Column(name = "uom_id", nullable = false, columnDefinition = "uuid")
    private UUID uomId;

    @Column(name = "quantity_ordered", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityOrdered;

    @Column(name = "quantity_received", nullable = false, precision = 19, scale = 6)
    @Builder.Default private BigDecimal quantityReceived = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    @Builder.Default private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "snapshot_name", length = 250)
    private String snapshotName;

    @Column(name = "snapshot_sku", length = 64)
    private String snapshotSku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GoodsReceiptLineStatus status = GoodsReceiptLineStatus.PENDING;
}
