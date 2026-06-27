package com.hisaberp.purchase.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_orders_tenant_number", columnNames = {"tenant_id", "number"}),
        indexes = {
                @Index(name = "idx_purchase_orders_party", columnList = "party_id"),
                @Index(name = "idx_purchase_orders_status", columnList = "status"),
                @Index(name = "idx_purchase_orders_warehouse", columnList = "warehouse_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class PurchaseOrder extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "party_id", nullable = false, columnDefinition = "uuid")
    private UUID partyId;

    // Optional hint for where goods are expected. The actual reception now
    // happens on a GoodsReceipt anchored to the converted purchase invoice,
    // which carries its own warehouse — so this is nullable.
    @Column(name = "warehouse_id", columnDefinition = "uuid")
    private UUID warehouseId;

    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MRU";

    @Column(precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 1000)
    private String notes;

    @Column(name = "converted_to_invoice_id", columnDefinition = "uuid")
    private UUID convertedToInvoiceId;
}
