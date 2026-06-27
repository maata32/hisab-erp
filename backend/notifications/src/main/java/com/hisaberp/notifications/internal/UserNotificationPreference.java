package com.hisaberp.notifications.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * CDC §3.12.2 — per-user opt-in/out for a specific event, overriding the tenant config.
 */
@Entity
@Table(name = "user_notification_preferences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_notification_preferences",
                columnNames = {"user_id", "event_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class UserNotificationPreference extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "event_code", nullable = false, length = 60)
    private String eventCode;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(columnDefinition = "text")
    private String channels;
}
