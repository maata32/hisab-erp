package com.hisaberp.sales.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "credit_note_lines",
        indexes = {
                @Index(name = "idx_credit_note_lines_note", columnList = "credit_note_id"),
                @Index(name = "idx_credit_note_lines_invoice_line", columnList = "invoice_line_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class CreditNoteLine extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "credit_note_id", nullable = false, columnDefinition = "uuid")
    private UUID creditNoteId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "invoice_line_id", columnDefinition = "uuid")
    private UUID invoiceLineId;

    @Column(name = "variant_id", columnDefinition = "uuid")
    private UUID variantId;

    @Column(name = "product_id", nullable = false, columnDefinition = "uuid")
    private UUID productId;

    @Column(name = "uom_id", nullable = false, columnDefinition = "uuid")
    private UUID uomId;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "discount_percent", precision = 5, scale = 2, nullable = false)
    @Builder.Default private BigDecimal discountPercent = BigDecimal.ZERO;

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
