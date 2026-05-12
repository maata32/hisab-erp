package com.minierp.notifications.internal;

import com.minierp.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * CDC §3.12.2 — message template for one event/channel/locale combo.
 * tenantId is nullable: a null row is a system-wide default template.
 */
@Entity
@Table(name = "notification_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_templates",
                columnNames = {"tenant_id", "event_code", "channel", "locale"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class NotificationTemplate extends AuditableEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "event_code", nullable = false, length = 60)
    private String eventCode;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
