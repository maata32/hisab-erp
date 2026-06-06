package com.minierp.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByOrganizationId(UUID organizationId);
    List<Subscription> findByStatusAndPeriodEndBefore(Subscription.SubscriptionStatus status, Instant cutoff);
}
