package com.minierp.tenant.api;

import java.math.BigDecimal;
import java.util.List;

/** Subscription revenue breakdowns (cancelled payments excluded). */
public record SubscriptionRevenueDto(
        BigDecimal total,
        List<Bucket> byMonth,
        List<Bucket> byPlan,
        List<Bucket> byTenant
) {
    /** key = stable id (month "yyyy-MM", plan code, org id) ; label = human label. */
    public record Bucket(String key, String label, BigDecimal amount) {}
}
