package com.minierp.pos.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One business shift on a register. Only one session per register can be in
 * status OPEN at any time (enforced by partial unique index on register_id WHERE status='OPEN').
 */
@Entity
@Table(name = "cash_sessions",
        indexes = {
                @Index(name = "idx_cash_sessions_register", columnList = "register_id"),
                @Index(name = "idx_cash_sessions_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CashSession extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "register_id", columnDefinition = "uuid", nullable = false)
    private UUID registerId;

    @Column(name = "cashier_user_id", columnDefinition = "uuid", nullable = false)
    private UUID cashierUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CashSessionStatus status = CashSessionStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "opening_float", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal openingFloat = BigDecimal.ZERO;

    @Column(name = "expected_closing", precision = 19, scale = 2)
    private BigDecimal expectedClosing;

    @Column(name = "counted_closing", precision = 19, scale = 2)
    private BigDecimal countedClosing;

    @Column(name = "difference", precision = 19, scale = 2)
    private BigDecimal difference;

    @Column(name = "total_sales", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "total_cash_in", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalCashIn = BigDecimal.ZERO;

    @Column(name = "total_cash_out", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalCashOut = BigDecimal.ZERO;

    @Column(length = 500)
    private String note;
}
