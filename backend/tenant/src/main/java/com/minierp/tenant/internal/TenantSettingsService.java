package com.minierp.tenant.internal;

import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.tenant.api.TenantSettingsDto;
import com.minierp.tenant.api.TenantSettingsLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantSettingsService implements TenantSettingsLookup {

    private final TenantSettingsRepository repo;

    @Override
    @Transactional(readOnly = true)
    public int getCurrencyDecimalPlaces(UUID tenantId) {
        return repo.findByOrganizationId(tenantId)
                .map(TenantSettings::getPosSettings)
                .map(m -> m.get("currencyDecimalPlaces"))
                .map(v -> v instanceof Number n ? n.intValue() : null)
                .filter(n -> n >= 0)
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public String getPaperSize(UUID tenantId) {
        return repo.findByOrganizationId(tenantId)
                .map(TenantSettings::getInvoiceSettings)
                .map(m -> m.get("paperSize"))
                .map(v -> v instanceof String s ? s : null)
                .filter(s -> "A4".equals(s) || "A5".equals(s))
                .orElse("A4");
    }

    @Transactional(readOnly = true)
    public TenantSettingsDto getForCurrentTenant() {
        UUID tenantId = CurrentUserHolder.require().tenantId();
        TenantSettings ts = repo.findByOrganizationId(tenantId)
                .orElseThrow(() -> NotFoundException.of("entity.tenant_settings", tenantId));
        return toDto(ts);
    }

    @Transactional
    public TenantSettingsDto update(UpdateRequest req) {
        UUID tenantId = CurrentUserHolder.require().tenantId();
        TenantSettings ts = repo.findByOrganizationId(tenantId)
                .orElseThrow(() -> NotFoundException.of("entity.tenant_settings", tenantId));
        if (req.posSettings() != null) ts.setPosSettings(req.posSettings());
        if (req.invoiceSettings() != null) ts.setInvoiceSettings(req.invoiceSettings());
        if (req.paymentSettings() != null) ts.setPaymentSettings(req.paymentSettings());
        if (req.notificationSettings() != null) ts.setNotificationSettings(req.notificationSettings());
        if (req.pricingSettings() != null) ts.setPricingSettings(req.pricingSettings());
        if (req.deliverySettings() != null) ts.setDeliverySettings(req.deliverySettings());
        return toDto(ts);
    }

    public record UpdateRequest(
            Map<String, Object> posSettings,
            Map<String, Object> invoiceSettings,
            Map<String, Object> paymentSettings,
            Map<String, Object> notificationSettings,
            Map<String, Object> pricingSettings,
            Map<String, Object> deliverySettings) {}

    private TenantSettingsDto toDto(TenantSettings ts) {
        return new TenantSettingsDto(
                ts.getPosSettings(),
                ts.getInvoiceSettings(),
                ts.getPaymentSettings(),
                ts.getNotificationSettings(),
                ts.getPricingSettings(),
                ts.getDeliverySettings());
    }
}
