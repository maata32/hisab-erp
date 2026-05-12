package com.minierp.notifications.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TenantNotificationConfigRepository extends JpaRepository<TenantNotificationConfig, UUID> {
    Optional<TenantNotificationConfig> findByEventCode(String eventCode);
    List<TenantNotificationConfig> findAllByOrderByEventCodeAsc();
}
