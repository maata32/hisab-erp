package com.hisaberp.tenant.internal;

import com.hisaberp.tenant.events.TenantTrialExpiringEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Daily tenant lifecycle sweep. Runs without a tenant context — the organizations
 * and subscriptions tables are not tenant-scoped (no tenant_id / no RLS), so a
 * cross-tenant scan is safe. All transitions go through {@link OrganizationService}
 * so cache eviction, status events and audit stay consistent.
 *
 * <p>Flow: TRIAL/ACTIVE lapsed → PAST_DUE (grace starts); PAST_DUE older than the
 * grace period → SUSPENDED; trials nearing their end get reminder e-mails.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
class TenantExpiryJob {

    /** Days-before-end at which a trial reminder e-mail is sent. */
    private static final Set<Integer> REMINDER_DAYS = Set.of(7, 3, 1);
    private static final long MAX_REMINDER_DAYS = 7;

    private final OrganizationRepository orgs;
    private final SubscriptionRepository subscriptions;
    private final OrganizationService organizationService;
    private final ApplicationEventPublisher events;

    @Value("${app.tenant.grace-period-days:7}")
    private long graceDays;

    @Scheduled(cron = "${app.tenant.expiry-cron:0 0 7 * * *}")
    public void sweep() {
        Instant now = Instant.now();

        // 1. Trials whose end date has passed → PAST_DUE.
        orgs.findAllByStatusAndTrialEndsAtBefore(OrganizationStatus.TRIAL, now)
                .forEach(o -> safe(() -> organizationService.markPastDue(o.getId()), o.getCode()));

        // 2. Paid subscriptions whose period has ended → PAST_DUE.
        subscriptions.findByStatusAndPeriodEndBefore(Subscription.SubscriptionStatus.ACTIVE, now)
                .forEach(s -> safe(() -> organizationService.markPastDue(s.getOrganizationId()), "sub:" + s.getOrganizationId()));

        // 3. PAST_DUE longer than the grace period → SUSPENDED.
        Instant graceCutoff = now.minus(graceDays, ChronoUnit.DAYS);
        orgs.findAllByStatusAndPastDueSinceBefore(OrganizationStatus.PAST_DUE, graceCutoff)
                .forEach(o -> safe(() -> organizationService.suspend(o.getId(), "auto:grace_elapsed"), o.getCode()));

        // 4. Trial reminders at 7 / 3 / 1 days before the end.
        orgs.findAllByStatusAndTrialEndsAtBetween(OrganizationStatus.TRIAL, now, now.plus(MAX_REMINDER_DAYS, ChronoUnit.DAYS))
                .forEach(o -> {
                    if (o.getTrialEndsAt() == null) return;
                    long hours = ChronoUnit.HOURS.between(now, o.getTrialEndsAt());
                    int daysLeft = (int) Math.ceil(hours / 24.0);
                    if (REMINDER_DAYS.contains(daysLeft)) {
                        events.publishEvent(new TenantTrialExpiringEvent(
                                o.getId(), o.getCode(), o.getName(), o.getEmail(), daysLeft, o.getLocale(), now));
                    }
                });
    }

    private void safe(Runnable action, String label) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("Tenant expiry sweep failed for {}: {}", label, ex.getMessage());
        }
    }
}
