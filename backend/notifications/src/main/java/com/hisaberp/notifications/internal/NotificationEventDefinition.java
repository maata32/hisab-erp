package com.hisaberp.notifications.internal;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * CDC §3.12.2 — system-wide catalog of notification event codes.
 * Read-only at runtime; seeded by Liquibase 0026 with the 20 events from CDC §3.12.1.
 */
@Entity
@Table(name = "notification_event_definitions",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_event_def_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class NotificationEventDefinition {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 30)
    private String category;

    /** Comma-separated list: SMS,EMAIL,IN_APP */
    @Column(name = "default_channels", length = 200)
    private String defaultChannels;

    /** Comma-separated list of role codes: CUSTOMER,MANAGER,STOCK_KEEPER,... */
    @Column(name = "default_recipients", length = 200)
    private String defaultRecipients;

    @Column(name = "default_enabled", nullable = false)
    @Builder.Default
    private boolean defaultEnabled = true;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String severity = "INFO";

    /** Comma-separated list of placeholder names (without braces). */
    @Column(length = 500)
    private String variables;
}
