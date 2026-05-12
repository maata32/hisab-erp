package com.minierp.notifications.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * CDC §3.12.2 — per-tenant override of notification dispatching for one event code.
 * channels / recipients stored as JSONB strings.
 */
@Entity
@Table(name = "tenant_notification_configs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tenant_notification_configs",
                columnNames = {"tenant_id", "event_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TenantNotificationConfig extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "event_code", nullable = false, length = 60)
    private String eventCode;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(columnDefinition = "jsonb")
    private String channels;

    @Column(columnDefinition = "jsonb")
    private String recipients;

    @Column(name = "custom_roles", length = 500)
    private String customRoles;

    @Column(name = "custom_users", length = 500)
    private String customUsers;
}
