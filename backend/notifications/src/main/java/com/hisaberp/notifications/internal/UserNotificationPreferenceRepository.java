package com.hisaberp.notifications.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserNotificationPreferenceRepository extends JpaRepository<UserNotificationPreference, UUID> {
    List<UserNotificationPreference> findByUserId(UUID userId);
    Optional<UserNotificationPreference> findByUserIdAndEventCode(UUID userId, String eventCode);
}
