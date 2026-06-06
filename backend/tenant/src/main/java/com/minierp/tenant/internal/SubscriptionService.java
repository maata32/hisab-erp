package com.minierp.tenant.internal;

import com.minierp.shared.error.ValidationException;
import com.minierp.tenant.internal.Subscription.BillingCycle;
import com.minierp.tenant.internal.Subscription.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the {@link Subscription} row that backs a tenant's billing lifecycle.
 * Called from {@link OrganizationService} as the organization status changes.
 */
@Service
@RequiredArgsConstructor
class SubscriptionService {

    private static final long ANNUAL_DAYS = 365;
    private static final long MONTHLY_DAYS = 30;

    private final SubscriptionRepository subscriptions;

    /** Creates (or resets) the trial subscription. No-op when the tenant chose no plan. */
    void startTrial(UUID organizationId, UUID planId, Instant trialEndsAt) {
        if (planId == null) return;
        Subscription sub = subscriptions.findByOrganizationId(organizationId).orElseGet(Subscription::new);
        Instant now = Instant.now();
        sub.setOrganizationId(organizationId);
        sub.setPlanId(planId);
        sub.setStatus(SubscriptionStatus.TRIAL);
        sub.setBillingCycle(BillingCycle.MONTHLY);
        sub.setPeriodStart(now);
        sub.setPeriodEnd(trialEndsAt);
        sub.setNextBillingDate(trialEndsAt);
        subscriptions.save(sub);
    }

    /** Activates a paid subscription for the given billing cycle. Requires a plan. */
    void activate(UUID organizationId, UUID planId, BillingCycle cycle) {
        if (planId == null) {
            throw new ValidationException("tenant.plan_required", Map.of());
        }
        Subscription sub = subscriptions.findByOrganizationId(organizationId).orElseGet(Subscription::new);
        Instant now = Instant.now();
        Instant end = now.plus(cycle == BillingCycle.ANNUAL ? ANNUAL_DAYS : MONTHLY_DAYS, ChronoUnit.DAYS);
        sub.setOrganizationId(organizationId);
        sub.setPlanId(planId);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setBillingCycle(cycle);
        sub.setPeriodStart(now);
        sub.setPeriodEnd(end);
        sub.setNextBillingDate(end);
        subscriptions.save(sub);
    }

    /** Mirrors a tenant status change onto its subscription (e.g. PAST_DUE, SUSPENDED). */
    void markStatus(UUID organizationId, SubscriptionStatus status) {
        subscriptions.findByOrganizationId(organizationId).ifPresent(s -> s.setStatus(status));
    }
}
