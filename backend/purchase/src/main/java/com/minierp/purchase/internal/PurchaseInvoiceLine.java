package com.minierp.purchase.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_invoice_lines",
        indexes = @Index(name = "idx_purchase_invoice_lines_inv", columnList = "purchase_invoice_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class PurchaseInvoiceLine extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "purchase_invoice_id", nullable = false, columnDefinition = "uuid")
    private UUID purchaseInvoiceId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "product_id", columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "uom_id", columnDefinition = "uuid")
    private UUID uomId;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "snapshot_name", length = 250)
    private String snapshotName;

    @Column(name = "snapshot_sku", length = 64)
    private String snapshotSku;
}
