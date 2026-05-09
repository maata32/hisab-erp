package com.minierp.tenant.internal;

import com.minierp.shared.audit.AuditEvent;
import com.minierp.shared.audit.RequestContext;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.util.PageResponse;
import com.minierp.tenant.api.CreateOrganizationRequest;
import com.minierp.tenant.api.OrganizationApi;
import com.minierp.tenant.api.OrganizationDto;
import com.minierp.tenant.api.OrganizationController.UpdateOrganizationRequest;
import com.minierp.tenant.events.OrganizationCreatedEvent;
import com.minierp.tenant.events.OrganizationStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService implements OrganizationApi {

    private final OrganizationRepository orgs;
    private final TenantSettingsRepository settings;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<OrganizationDto> list(Pageable pageable) {
        return PageResponse.of(orgs.findAll(pageable).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public OrganizationDto get(UUID id) {
        Organization o = orgs.findById(id).orElseThrow(() -> NotFoundException.of("entity.organization", id));
        return toDto(o);
    }

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId"}, allEntries = true)
    public OrganizationDto create(CreateOrganizationRequest req) {
        if (orgs.existsByCode(req.code())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", req.code()));
        }
        Organization org = Organization.builder()
                .code(req.code())
                .name(req.name())
                .type(OrganizationType.valueOf(req.type()))
                .currency(req.currency() == null ? "MRU" : req.currency())
                .locale(req.locale() == null ? "fr" : req.locale())
                .timezone(req.timezone() == null ? "Africa/Nouakchott" : req.timezone())
                .email(req.email())
                .phone(req.phone())
                .address(req.address())
                .status(OrganizationStatus.TRIAL)
                .trialEndsAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .subscriptionPlanId(req.subscriptionPlanId())
                .build();
        orgs.save(org);

        TenantSettings ts = TenantSettings.builder()
                .organizationId(org.getId())
                .deliverySettings(defaultDeliverySettings())
                .posSettings(defaultPosSettings())
                .paymentSettings(defaultPaymentSettings())
                .expirySettings(defaultExpirySettings())
                .invoiceSettings(defaultInvoiceSettings())
                .pricingSettings(defaultPricingSettings())
                .notificationSettings(Map.of())
                .customSettings(Map.of())
                .build();
        settings.save(ts);

        events.publishEvent(new OrganizationCreatedEvent(org.getId(), org.getCode(), Instant.now()));
        events.publishEvent(audit(org, "ORG_CREATED", Map.of("code", org.getCode())));
        return toDto(org);
    }

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId"}, allEntries = true)
    public OrganizationDto update(UUID id, UpdateOrganizationRequest req) {
        Organization o = orgs.findById(id).orElseThrow(() -> NotFoundException.of("entity.organization", id));
        if (req.name() != null) o.setName(req.name());
        if (req.email() != null) o.setEmail(req.email());
        if (req.phone() != null) o.setPhone(req.phone());
        if (req.address() != null) o.setAddress(req.address());
        if (req.logoUrl() != null) o.setLogoUrl(req.logoUrl());
        if (req.locale() != null) o.setLocale(req.locale());
        if (req.timezone() != null) o.setTimezone(req.timezone());
        return toDto(o);
    }

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId"}, allEntries = true)
    public void suspend(UUID id, String reason) {
        Organization o = orgs.findById(id).orElseThrow(() -> NotFoundException.of("entity.organization", id));
        OrganizationStatus old = o.getStatus();
        o.setStatus(OrganizationStatus.SUSPENDED);
        events.publishEvent(new OrganizationStatusChangedEvent(o.getId(), old.name(), o.getStatus().name(), reason, Instant.now()));
        events.publishEvent(audit(o, "ORG_SUSPENDED", Map.of("reason", reason == null ? "" : reason)));
    }

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId"}, allEntries = true)
    public void reactivate(UUID id) {
        Organization o = orgs.findById(id).orElseThrow(() -> NotFoundException.of("entity.organization", id));
        OrganizationStatus old = o.getStatus();
        o.setStatus(OrganizationStatus.ACTIVE);
        events.publishEvent(new OrganizationStatusChangedEvent(o.getId(), old.name(), o.getStatus().name(), null, Instant.now()));
        events.publishEvent(audit(o, "ORG_REACTIVATED", Map.of()));
    }

    private OrganizationDto toDto(Organization o) {
        return new OrganizationDto(
                o.getId(), o.getCode(), o.getName(), o.getType().name(),
                o.getStatus().name(), o.getCurrency(), o.getLocale(), o.getTimezone(),
                o.getEmail(), o.getPhone(), o.getAddress());
    }

    private AuditEvent audit(Organization org, String action, Map<String, Object> details) {
        var ctx = RequestContext.tryGet().orElse(null);
        UUID actor = CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
        return AuditEvent.builder()
                .tenantId(org.getId())
                .actorUserId(actor)
                .action(action)
                .entityType("Organization")
                .entityId(org.getId().toString())
                .ipAddress(ctx != null ? ctx.ipAddress() : null)
                .userAgent(ctx != null ? ctx.userAgent() : null)
                .newValue(details)
                .build();
    }

    private static Map<String, Object> defaultDeliverySettings() {
        return Map.of(
                "enabled", true,
                "enabledByDefaultForPos", false,
                "enabledByDefaultForSales", true,
                "allowPartialDelivery", true,
                "requireDeliveryAddress", true,
                "invoicingMode", "ON_DELIVERY",
                "stockReservationMode", "ON_ORDER",
                "defaultLeadTimeDays", 2);
    }

    private static Map<String, Object> defaultPosSettings() {
        return Map.of(
                "thermalReceiptWidth", 80,
                "allowDiscounts", true,
                "maxDiscountPercent", 30,
                "defaultPaymentMethod", "CASH",
                "requireCustomerForCredit", true);
    }

    private static Map<String, Object> defaultPaymentSettings() {
        return Map.of(
                "enabledMethods", java.util.List.of("CASH", "BANK_TRANSFER", "MOBILE_MONEY", "CHECK", "CARD"),
                "defaultMethod", "CASH",
                "autoAllocationStrategy", "FIFO",
                "allowOverpayment", true,
                "overpaymentBehavior", "CREATE_CREDIT",
                "requireReceiptForCash", true,
                "creditLimitBehavior", "WARN",
                "defaultPaymentTermsDays", 30,
                "overdueGracePeriodDays", 5);
    }

    private static Map<String, Object> defaultExpirySettings() {
        return Map.of(
                "enabled", true,
                "forceFEFO", true,
                "allowSellExpired", false,
                "warnOnNearExpiry", true,
                "defaultShelfLifeDays", 365,
                "alertThresholds", java.util.List.of(30, 15, 7, 1));
    }

    private static Map<String, Object> defaultInvoiceSettings() {
        return Map.of(
                "taxEnabled", true,
                "defaultTaxRate", 0.16,
                "pricesIncludeTax", false,
                "numberPrefix", "FAC");
    }

    private static Map<String, Object> defaultPricingSettings() {
        return Map.of(
                "costMethod", "CMP",
                "defaultPriceTier", "RETAIL");
    }
}
