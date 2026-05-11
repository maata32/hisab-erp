package com.minierp.lotexpiry.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ExpiryAlertConfigRepository extends JpaRepository<ExpiryAlertConfig, UUID> {

    List<ExpiryAlertConfig> findByEnabledTrue();
}
