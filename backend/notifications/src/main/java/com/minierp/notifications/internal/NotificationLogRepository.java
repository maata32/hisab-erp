package com.minierp.notifications.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByEventCode(String eventCode, Pageable pageable);

    List<NotificationLog> findByStatusAndNextRetryAtBefore(String status, Instant cutoff);
}
