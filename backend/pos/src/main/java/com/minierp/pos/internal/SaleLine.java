package com.minierp.pos.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "sale_lines",
        indexes = {
                @Index(name = "idx_sale_lines_sale", columnList = "sale_id"),
                @Index(name = "idx_sale_lines_product", columnList = "product_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SaleLine extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "sale_id", columnDefinition = "uuid", nullable = false)
    private UUID saleId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "variant_id", columnDefinition = "uuid")
    private UUID variantId;

    @Column(name = "product_id", columnDefinition = "uuid", nullable = false)
    private UUID productId;

    @Column(name = "lot_id", columnDefinition = "uuid")
    private UUID lotId;

    /** UoM the line was sold in (may be a packaging UoM, e.g. carton). */
    @Column(name = "uom_id", columnDefinition = "uuid", nullable = false)
    private UUID uomId;

    /** Quantity in the line UoM. */
    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    /** Quantity converted to the product's base UoM (used to deduct stock). */
    @Column(name = "base_quantity", nullable = false, precision = 19, scale = 6)
    private BigDecimal baseQuantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "unit_discount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal unitDiscount = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "tax_inclusive", nullable = false)
    private boolean taxInclusive;

    @Column(name = "snapshot_name", length = 250)
    private String snapshotName;

    @Column(name = "snapshot_sku", length = 64)
    private String snapshotSku;
}
