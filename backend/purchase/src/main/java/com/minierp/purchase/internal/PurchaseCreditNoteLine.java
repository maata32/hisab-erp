package com.minierp.purchase.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_credit_note_lines",
        indexes = {
                @Index(name = "idx_purchase_credit_note_lines_note", columnList = "purchase_credit_note_id"),
                @Index(name = "idx_purchase_credit_note_lines_inv_line", columnList = "purchase_invoice_line_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class PurchaseCreditNoteLine extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "purchase_credit_note_id", nullable = false, columnDefinition = "uuid")
    private UUID purchaseCreditNoteId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "purchase_invoice_line_id", columnDefinition = "uuid")
    private UUID purchaseInvoiceLineId;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "uom_id", nullable = false, columnDefinition = "uuid")
    private UUID uomId;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "returned_to_stock_qty", nullable = false, precision = 19, scale = 6)
    @Builder.Default private BigDecimal returnedToStockQty = BigDecimal.ZERO;

    @Column(name = "snapshot_name", length = 250)
    private String snapshotName;

    @Column(name = "snapshot_sku", length = 64)
    private String snapshotSku;
}
