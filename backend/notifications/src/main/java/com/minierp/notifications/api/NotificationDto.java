package com.minierp.notifications.api;

import java.util.List;

public final class NotificationDto {

    private NotificationDto() {}

    /** CDC §3.12.2 — element of the event catalog. */
    public record EventDefinition(
            String code,
            String name,
            String description,
            String category,
            List<String> defaultChannels,
            List<String> defaultRecipients,
            boolean defaultEnabled,
            String severity,
            List<String> variables) {}

    /** CDC §3.12.2 — per-tenant notification override for one event. */
    public record TenantConfig(
            String eventCode,
            boolean enabled,
            String channels,
            String recipients,
            String customRoles,
            String customUsers) {}

    /** PUT /notifications/config/{eventCode} body. */
    public record TenantConfigRequest(
            boolean enabled,
            String channels,
            String recipients,
            String customRoles,
            String customUsers) {}
}
