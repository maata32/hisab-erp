package com.minierp.tenant.internal;

import com.minierp.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A subscription payment received from a tenant (super-admin ledger). Recording one extends the
 * tenant's subscription period by {@code years + months} and sets it ACTIVE. Global table —
 * no tenant scoping.
 */
@Entity
@Table(name = "subscription_payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class SubscriptionPayment extends AuditableEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(nullable = false)
    @Builder.Default
    private int years = 0;

    @Column(nullable = false)
    @Builder.Default
    private int months = 0;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "paid_at", nullable = false)
    private LocalDate paidAt;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "attachment_url", length = 1000)
    private String attachmentUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean cancelled = false;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
