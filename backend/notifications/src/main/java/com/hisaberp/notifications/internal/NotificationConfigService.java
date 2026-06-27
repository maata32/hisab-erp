package com.hisaberp.notifications.internal;

import com.hisaberp.notifications.api.NotificationDto;
import com.hisaberp.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * CDC §3.12 — coordinates the event catalog, the per-tenant overrides,
 * and exposes them to the REST layer. The actual dispatch/listener
 * mechanics live alongside, in {@code NotificationDispatchService} (TODO).
 */
@Service
@RequiredArgsConstructor
public class NotificationConfigService {

    private final NotificationEventDefinitionRepository events;
    private final TenantNotificationConfigRepository tenantConfigs;

    @Transactional(readOnly = true)
    public List<NotificationDto.EventDefinition> listEvents() {
        return events.findAll().stream()
                .map(e -> new NotificationDto.EventDefinition(
                        e.getCode(), e.getName(), e.getDescription(),
                        e.getCategory(), split(e.getDefaultChannels()),
                        split(e.getDefaultRecipients()),
                        e.isDefaultEnabled(), e.getSeverity(),
                        split(e.getVariables())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NotificationDto.TenantConfig> listTenantConfig() {
        return tenantConfigs.findAllByOrderByEventCodeAsc().stream()
                .map(this::toConfigDto)
                .toList();
    }

    @Transactional
    public NotificationDto.TenantConfig upsertConfig(
            String eventCode, boolean enabled,
            String channelsJson, String recipientsJson,
            String customRoles, String customUsers) {

        events.findByCode(eventCode)
                .orElseThrow(() -> NotFoundException.of("entity.notification_event", eventCode));

        TenantNotificationConfig cfg = tenantConfigs.findByEventCode(eventCode)
                .orElseGet(() -> TenantNotificationConfig.builder().eventCode(eventCode).build());
        cfg.setEnabled(enabled);
        cfg.setChannels(channelsJson);
        cfg.setRecipients(recipientsJson);
        cfg.setCustomRoles(customRoles);
        cfg.setCustomUsers(customUsers);
        return toConfigDto(tenantConfigs.save(cfg));
    }

    @Transactional
    public void resetToDefaults() {
        tenantConfigs.deleteAll();
    }

    private NotificationDto.TenantConfig toConfigDto(TenantNotificationConfig c) {
        return new NotificationDto.TenantConfig(
                c.getEventCode(), c.isEnabled(),
                c.getChannels(), c.getRecipients(),
                c.getCustomRoles(), c.getCustomUsers());
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return List.of(csv.split(","));
    }

    /** Used by future dispatch listeners to look up the right template. */
    @Transactional(readOnly = true)
    public boolean isEventEnabled(UUID tenantId, String eventCode) {
        return tenantConfigs.findByEventCode(eventCode)
                .map(TenantNotificationConfig::isEnabled)
                .orElseGet(() -> events.findByCode(eventCode)
                        .map(NotificationEventDefinition::isDefaultEnabled)
                        .orElse(false));
    }
}
