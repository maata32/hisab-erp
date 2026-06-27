package com.hisaberp.notifications.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface NotificationEventDefinitionRepository extends JpaRepository<NotificationEventDefinition, UUID> {
    Optional<NotificationEventDefinition> findByCode(String code);
}
