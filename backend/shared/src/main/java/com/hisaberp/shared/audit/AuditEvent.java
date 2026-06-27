package com.hisaberp.shared.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Published from any module when a sensitive operation occurs.
 * The audit module persists it asynchronously into the immutable audit_log table.
 */
public record AuditEvent(
        UUID tenantId,
        UUID actorUserId,
        String action,
        String entityType,
        String entityId,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String ipAddress,
        String userAgent,
        Instant occurredAt
) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private UUID tenantId;
        private UUID actorUserId;
        private String action;
        private String entityType;
        private String entityId;
        private Map<String, Object> oldValue = Map.of();
        private Map<String, Object> newValue = Map.of();
        private String ipAddress;
        private String userAgent;
        private Instant occurredAt = Instant.now();

        public Builder tenantId(UUID v) { this.tenantId = v; return this; }
        public Builder actorUserId(UUID v) { this.actorUserId = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder entityType(String v) { this.entityType = v; return this; }
        public Builder entityId(String v) { this.entityId = v; return this; }
        public Builder oldValue(Map<String, Object> v) { this.oldValue = v == null ? Map.of() : v; return this; }
        public Builder newValue(Map<String, Object> v) { this.newValue = v == null ? Map.of() : v; return this; }
        public Builder ipAddress(String v) { this.ipAddress = v; return this; }
        public Builder userAgent(String v) { this.userAgent = v; return this; }
        public Builder occurredAt(Instant v) { this.occurredAt = v; return this; }

        public AuditEvent build() {
            return new AuditEvent(tenantId, actorUserId, action, entityType, entityId,
                    oldValue, newValue, ipAddress, userAgent, occurredAt);
        }
    }
}
