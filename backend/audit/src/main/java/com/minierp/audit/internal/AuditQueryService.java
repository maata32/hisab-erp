package com.minierp.audit.internal;

import com.minierp.audit.api.AuditController.AuditDto;
import com.minierp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository repo;

    @Transactional(readOnly = true)
    public PageResponse<AuditDto> list(UUID tenantId, Instant from, Instant to, Pageable pageable) {
        return PageResponse.of(repo
                .findByTenantIdAndOccurredAtBetweenOrderByOccurredAtDesc(tenantId, from, to, pageable)
                .map(this::toDto));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditDto> listByEntity(UUID tenantId, String entityType, String entityId, Pageable pageable) {
        return PageResponse.of(repo
                .findByTenantIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(tenantId, entityType, entityId, pageable)
                .map(this::toDto));
    }

    private AuditDto toDto(AuditLog log) {
        return new AuditDto(
                log.getId(), log.getTenantId(), log.getActorUserId(), log.getAction(),
                log.getEntityType(), log.getEntityId(),
                log.getOldValue(), log.getNewValue(),
                log.getIpAddress(), log.getUserAgent(), log.getOccurredAt());
    }
}
