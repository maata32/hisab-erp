package com.minierp.pos.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A completed POS transaction. The {@code idempotencyKey} is unique per tenant and
 * makes the {@code POST /pos/sales} endpoint replay-safe — second submission with the
 * same key returns the existing sale.
 */
@Entity
@Table(name = "sales",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sales_idempotency",
                        columnNames = {"tenant_id", "idempotency_key"}),
                @UniqueConstraint(name = "uk_sales_number",
                        columnNames = {"tenant_id", "number"})
        },
        indexes = {
                @Index(name = "idx_sales_session", columnList = "session_id"),
                @Index(name = "idx_sales_register", columnList = "register_id"),
                @Index(name = "idx_sales_completed", columnList = "completed_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Sale extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "register_id", columnDefinition = "uuid", nullable = false)
    private UUID registerId;

    @Column(name = "session_id", columnDefinition = "uuid", nullable = false)
    private UUID sessionId;

    @Column(name = "warehouse_id", columnDefinition = "uuid", nullable = false)
    private UUID warehouseId;

    @Column(name = "cashier_user_id", columnDefinition = "uuid", nullable = false)
    private UUID cashierUserId;

    @Column(name = "customer_id", columnDefinition = "uuid")
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SaleStatus status = SaleStatus.COMPLETED;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MRU";

    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "paid_cash", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal paidCash = BigDecimal.ZERO;

    @Column(name = "paid_card", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal paidCard = BigDecimal.ZERO;

    @Column(name = "paid_mobile", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal paidMobile = BigDecimal.ZERO;

    @Column(name = "paid_credit", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal paidCredit = BigDecimal.ZERO;

    @Column(name = "change_due", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal changeDue = BigDecimal.ZERO;

    @Column(name = "completed_at", nullable = false)
    @Builder.Default
    private Instant completedAt = Instant.now();

    @Column(length = 1000)
    private String note;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by", columnDefinition = "uuid")
    private UUID voidedBy;

    @Column(name = "void_reason", length = 500)
    private String voidReason;
}
