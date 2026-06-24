package com.minierp.notifications.internal;

import com.minierp.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * CDC §3.12.2 — outbound notification audit + retry tracking.
 * Append-style usage; status transitions QUEUED → SENT → DELIVERED, with FAILED/BOUNCED on errors.
 */
@Entity
@Table(name = "notification_logs",
        indexes = {
                @Index(name = "idx_notification_logs_tenant_event",
                       columnList = "tenant_id, event_code"),
                @Index(name = "idx_notification_logs_status",
                       columnList = "status, next_retry_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class NotificationLog extends AuditableEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "event_code", nullable = false, length = 60)
    private String eventCode;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "party_id", columnDefinition = "uuid")
    private UUID partyId;

    @Column(name = "template_id", columnDefinition = "uuid")
    private UUID templateId;

    @Column(length = 10)
    private String locale;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "QUEUED";

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "provider_name", length = 60)
    private String providerName;

    @Column(name = "provider_ref", length = 120)
    private String providerRef;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "related_entity_type", length = 60)
    private String relatedEntityType;

    @Column(name = "related_entity_id", columnDefinition = "uuid")
    private UUID relatedEntityId;
}
