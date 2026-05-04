package com.minierp.audit.internal;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable, append-only. Updates and deletes are blocked at the DB level
 * via a trigger (see Liquibase changeset). Retained 5 years hot, archived to S3 thereafter (ADR-013).
 */
@Entity
@Table(name = "audit_log",
        indexes = {
                @Index(name = "idx_audit_log_tenant_time", columnList = "tenant_id, occurred_at"),
                @Index(name = "idx_audit_log_actor_time", columnList = "actor_user_id, occurred_at"),
                @Index(name = "idx_audit_log_entity", columnList = "entity_type, entity_id")
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class AuditLog {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    @Column(name = "actor_user_id", columnDefinition = "uuid")
    private UUID actorUserId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private Map<String, Object> newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
