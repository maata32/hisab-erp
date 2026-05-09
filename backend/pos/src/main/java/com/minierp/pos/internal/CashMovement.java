package com.minierp.pos.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cash in/out events on a session: opening float deposit, manual pay-in,
 * pay-out, and closing reconciliation. Sale-driven cash receipts are stored
 * on the Sale itself, not duplicated here.
 */
@Entity
@Table(name = "cash_movements",
        indexes = @Index(name = "idx_cash_movements_session", columnList = "session_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CashMovement extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "session_id", columnDefinition = "uuid", nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CashMovementType type;

    /** Positive in, negative out. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String reason;

    @Column(name = "occurred_at", nullable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;
}
