package com.minierp.audit.internal;

import com.minierp.shared.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class AuditEventListener {

    private final AuditLogRepository repo;

    @ApplicationModuleListener
    public void on(AuditEvent event) {
        try {
            AuditLog row = AuditLog.builder()
                    .tenantId(event.tenantId())
                    .actorUserId(event.actorUserId())
                    .action(event.action())
                    .entityType(event.entityType())
                    .entityId(event.entityId())
                    .oldValue(event.oldValue())
                    .newValue(event.newValue())
                    .ipAddress(event.ipAddress())
                    .userAgent(event.userAgent())
                    .occurredAt(event.occurredAt())
                    .build();
            repo.save(row);
        } catch (RuntimeException ex) {
            // Audit must NEVER break the parent transaction.
            log.error("Failed to persist audit event for action={} entity={}/{}: {}",
                    event.action(), event.entityType(), event.entityId(), ex.getMessage(), ex);
        }
    }
}
