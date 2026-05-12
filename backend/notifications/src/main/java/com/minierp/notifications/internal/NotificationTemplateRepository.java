package com.minierp.notifications.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    Optional<NotificationTemplate> findFirstByEventCodeAndChannelAndLocale(
            String eventCode, String channel, String locale);

    List<NotificationTemplate> findByEventCode(String eventCode);
}
