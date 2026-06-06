package com.minierp.tenant.api;

import java.time.Instant;
import java.util.UUID;

public record OrganizationDto(
        UUID id, String code, String name, String type,
        String status, String currency, String locale, String timezone,
        String email, String phone, String address,
        Instant trialEndsAt, Instant pastDueSince,
        String planCode, String subscriptionStatus,
        UUID primaryAdminUserId) {}
