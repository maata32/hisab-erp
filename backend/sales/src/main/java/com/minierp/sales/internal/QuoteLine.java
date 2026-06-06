package com.minierp.sales.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "quote_lines",
        indexes = @Index(name = "idx_quote_lines_quote", columnList = "quote_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class QuoteLine extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "quote_id", nullable = false, columnDefinition = "uuid")
    private UUID quoteId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

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

    @Column(name = "discount_percent", precision = 5, scale = 2)
    @Builder.Default private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    @Builder.Default private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "snapshot_name", length = 250)
    private String snapshotName;

    @Column(name = "snapshot_sku", length = 64)
    private String snapshotSku;
}
