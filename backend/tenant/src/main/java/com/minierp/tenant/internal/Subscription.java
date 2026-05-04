package com.minierp.tenant.internal;

import com.minierp.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_subscriptions_org", columnNames = "organization_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Subscription extends AuditableEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "plan_id", nullable = false, columnDefinition = "uuid")
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 10)
    @Builder.Default
    private BillingCycle billingCycle = BillingCycle.MONTHLY;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @Column(name = "next_billing_date")
    private Instant nextBillingDate;

    enum SubscriptionStatus { TRIAL, ACTIVE, PAST_DUE, CANCELLED, SUSPENDED }
    enum BillingCycle { MONTHLY, ANNUAL }
}
