package com.minierp.lotexpiry.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Runs daily at 06:00 to emit expiry alerts and at 06:30 to mark expired lots.
 * Operates without tenant context — queries across all tenants (RLS bypassed
 * via @Transactional on a superuser connection in production, harmless in tests).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ExpiryAlertJob {

    private final ProductLotRepository lots;
    private final ExpiryAlertConfigRepository alertConfigs;

    /** Scan lots that are approaching expiry and log alerts. */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional(readOnly = true)
    public void scanExpiringLots() {
        List<ExpiryAlertConfig> configs = alertConfigs.findByEnabledTrue();
        if (configs.isEmpty()) return;

        configs.forEach(config -> {
            LocalDate threshold = LocalDate.now().plusDays(config.getDaysBeforeExpiry());
            List<ProductLot> expiring = lots.findExpiringBefore(threshold);
            expiring.forEach(lot ->
                log.warn("[LOT-EXPIRY] {} severity={} daysBeforeExpiry={} lotId={} product={} expires={} qty={}",
                        config.getSeverity(), config.getSeverity(),
                        config.getDaysBeforeExpiry(), lot.getId(),
                        lot.getProductId(), lot.getExpirationDate(), lot.getQuantityRemaining()));
        });
    }

    /** Mark lots whose expiration date has passed as EXPIRED. */
    @Scheduled(cron = "0 30 6 * * *")
    @Transactional
    public void markExpiredLots() {
        int count = lots.markExpiredLotsBeforeDate(LocalDate.now());
        if (count > 0) {
            log.info("[LOT-EXPIRY] Marked {} lots as EXPIRED", count);
        }
    }
}
