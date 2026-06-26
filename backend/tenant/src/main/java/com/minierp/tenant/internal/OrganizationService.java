package com.minierp.tenant.internal;

import com.minierp.shared.audit.AuditEvent;
import com.minierp.shared.audit.RequestContext;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.error.ValidationException;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.util.PageResponse;
import com.minierp.tenant.api.CreateOrganizationRequest;
import com.minierp.tenant.api.OrganizationApi;
import com.minierp.tenant.api.OrganizationDto;
import com.minierp.tenant.api.OrganizationController.UpdateOrganizationRequest;
import com.minierp.tenant.api.RegisterOrganizationRequest;
import com.minierp.tenant.events.OrganizationCreatedEvent;
import com.minierp.tenant.events.OrganizationStatusChangedEvent;
import com.minierp.tenant.events.TenantApprovedEvent;
import com.minierp.tenant.events.TenantRejectedEvent;
import com.minierp.tenant.events.TenantSuspendedEvent;
import com.minierp.tenant.internal.Subscription.BillingCycle;
import com.minierp.tenant.internal.Subscription.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationService implements OrganizationApi {

    private static final long TRIAL_DAYS = 30;

    private final OrganizationRepository orgs;
    private final TenantSettingsRepository settings;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final SubscriptionService subscriptionService;
    private final OrganizationTypeService organizationTypes;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<OrganizationDto> list(String q, String status, String type, String plan, Pageable pageable) {
        Specification<Organization> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            // Always hide the reserved platform organization that homes super-admin accounts.
            ps.add(cb.notEqual(root.get("code"), OrganizationApi.PLATFORM_ORG_CODE));
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), parseStatus(status)));
            }
            if (type != null && !type.isBlank()) {
                ps.add(cb.equal(root.get("type"), type.trim().toUpperCase()));
            }
            if (plan != null && !plan.isBlank()) {
                UUID planId = plans.findByCode(plan).map(SubscriptionPlan::getId).orElse(null);
                ps.add(planId != null ? cb.equal(root.get("subscriptionPlanId"), planId) : cb.disjunction());
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return PageResponse.of(orgs.findAll(spec, pageable).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public OrganizationDto get(UUID id) {
        return toDto(load(id));
    }

    // ── Creation paths ───────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId", "tenants:branding", "tenants:limits"}, allEntries = true)
    public OrganizationDto create(CreateOrganizationRequest req) {
        requireCodeAvailable(req.code());
        Instant trialEnds = Instant.now().plus(TRIAL_DAYS, ChronoUnit.DAYS);
        Organization org = baseBuilder(req.code(), req.name(), req.type(),
                req.currency(), req.locale(), req.timezone(), req.email(), req.phone(), req.address())
                .status(OrganizationStatus.TRIAL)
                .trialEndsAt(trialEnds)
                .subscriptionPlanId(req.subscriptionPlanId())
                .build();
        orgs.save(org);
        seedSettings(org.getId());

        subscriptionService.startTrial(org.getId(), org.getSubscriptionPlanId(), trialEnds);
        events.publishEvent(new OrganizationCreatedEvent(org.getId(), org.getCode(), Instant.now()));
        events.publishEvent(audit(org, "ORG_CREATED", Map.of("code", org.getCode())));
        return toDto(org);
    }

    @Override
    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId", "tenants:branding", "tenants:limits"}, allEntries = true)
    public OrganizationDto register(RegisterOrganizationRequest req) {
        requireCodeAvailable(req.code());
        UUID planId = resolvePlanId(req.planCode());
        Organization org = baseBuilder(req.code(), req.name(), req.type(),
                req.currency(), req.locale(), req.timezone(), req.email(), req.phone(), req.address())
                .status(OrganizationStatus.PENDING)
                .subscriptionPlanId(planId)
                .build();
        orgs.save(org);
        seedSettings(org.getId());

        // Seed roles now so TENANT_ADMIN exists by the time a super-admin approves.
        events.publishEvent(new OrganizationCreatedEvent(org.getId(), org.getCode(), Instant.now()));
        events.publishEvent(audit(org, "ORG_REGISTERED", Map.of("code", org.getCode())));
        return toDto(org);
    }

    @Override
    @Transactional
    public void setPrimaryAdmin(UUID organizationId, UUID userId) {
        load(organizationId).setPrimaryAdminUserId(userId);
    }

    // ── Updates ──────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId", "tenants:branding"}, allEntries = true)
    public OrganizationDto update(UUID id, UpdateOrganizationRequest req) {
        Organization o = load(id);
        if (req.name() != null) o.setName(req.name());
        if (req.email() != null) o.setEmail(req.email());
        if (req.phone() != null) o.setPhone(req.phone());
        if (req.address() != null) o.setAddress(req.address());
        if (req.logoUrl() != null) o.setLogoUrl(req.logoUrl());
        if (req.locale() != null) o.setLocale(req.locale());
        if (req.timezone() != null) o.setTimezone(req.timezone());
        return toDto(o);
    }

    // ── Lifecycle transitions ────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId", "tenants:branding", "tenants:limits"}, allEntries = true)
    public OrganizationDto approve(UUID id) {
        Organization o = load(id);
        requireStatus(o, OrganizationStatus.PENDING);
        Instant trialEnds = Instant.now().plus(TRIAL_DAYS, ChronoUnit.DAYS);
        OrganizationStatus old = o.getStatus();
        o.setStatus(OrganizationStatus.TRIAL);
        o.setTrialEndsAt(trialEnds);
        o.setPastDueSince(null);

        subscriptionService.startTrial(o.getId(), o.getSubscriptionPlanId(), trialEnds);
        publishStatusChange(o, old, null);
        events.publishEvent(new TenantApprovedEvent(o.getId(), o.getCode(), o.getName(),
                o.getPrimaryAdminUserId(), o.getEmail(), o.getName(), o.getLocale(), Instant.now()));
        events.publishEvent(audit(o, "ORG_APPROVED", Map.of("code", o.getCode())));
        return toDto(o);
    }

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId", "tenants:branding", "tenants:limits"}, allEntries = true)
    public OrganizationDto reject(UUID id, String reason) {
        Organization o = load(id);
        requireStatus(o, OrganizationStatus.PENDING);
        OrganizationStatus old = o.getStatus();
        o.setStatus(OrganizationStatus.ARCHIVED);

        publishStatusChange(o, old, reason);
        events.publishEvent(new TenantRejectedEvent(o.getId(), o.getCode(), o.getName(),
                o.getEmail(), o.getName(), reason, o.getLocale(), Instant.now()));
        events.publishEvent(audit(o, "ORG_REJECTED", Map.of("reason", reason == null ? "" : reason)));
        return toDto(o);
    }

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId", "tenants:branding", "tenants:limits"}, allEntries = true)
    public OrganizationDto activate(UUID id, String planCode, String billingCycle) {
        Organization o = load(id);
        if (o.getStatus() == OrganizationStatus.PENDING || o.getStatus() == OrganizationStatus.ARCHIVED) {
            throw new ValidationException("tenant.invalid_transition",
                    Map.of("from", o.getStatus().name(), "to", "ACTIVE"));
        }
        if (planCode != null && !planCode.isBlank()) {
            o.setSubscriptionPlanId(resolvePlanId(planCode));
        }
        BillingCycle cycle = parseCycle(billingCycle);
        subscriptionService.activate(o.getId(), o.getSubscriptionPlanId(), cycle);

        OrganizationStatus old = o.getStatus();
        o.setStatus(OrganizationStatus.ACTIVE);
        o.setPastDueSince(null);
        publishStatusChange(o, old, null);
        events.publishEvent(audit(o, "ORG_ACTIVATED",
                Map.of("plan", planCode == null ? "" : planCode, "cycle", cycle.name())));
        return toDto(o);
    }

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId", "tenants:branding", "tenants:limits"}, allEntries = true)
    public void suspend(UUID id, String reason) {
        Organization o = load(id);
        OrganizationStatus old = o.getStatus();
        o.setStatus(OrganizationStatus.SUSPENDED);
        subscriptionService.markStatus(o.getId(), SubscriptionStatus.SUSPENDED);

        publishStatusChange(o, old, reason);
        events.publishEvent(new TenantSuspendedEvent(o.getId(), o.getCode(), o.getName(),
                o.getEmail(), reason, o.getLocale(), Instant.now()));
        events.publishEvent(audit(o, "ORG_SUSPENDED", Map.of("reason", reason == null ? "" : reason)));
    }

    /** Called by {@link TenantExpiryJob}: trial/subscription lapsed, enter the grace period. Idempotent. */
    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId", "tenants:branding", "tenants:limits"}, allEntries = true)
    public void markPastDue(UUID id) {
        Organization o = load(id);
        if (o.getStatus() != OrganizationStatus.TRIAL && o.getStatus() != OrganizationStatus.ACTIVE) {
            return;
        }
        OrganizationStatus old = o.getStatus();
        o.setStatus(OrganizationStatus.PAST_DUE);
        o.setPastDueSince(Instant.now());
        subscriptionService.markStatus(o.getId(), SubscriptionStatus.PAST_DUE);
        publishStatusChange(o, old, "auto:expired");
        events.publishEvent(audit(o, "ORG_PAST_DUE", Map.of()));
    }

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId", "tenants:branding", "tenants:limits"}, allEntries = true)
    public void reactivate(UUID id) {
        Organization o = load(id);
        OrganizationStatus old = o.getStatus();
        o.setStatus(OrganizationStatus.ACTIVE);
        o.setPastDueSince(null);
        subscriptionService.markStatus(o.getId(), SubscriptionStatus.ACTIVE);
        publishStatusChange(o, old, null);
        events.publishEvent(audit(o, "ORG_REACTIVATED", Map.of()));
    }

    @Transactional
    @CacheEvict(value = {"tenants:byCode", "tenants:byId", "tenants:branding", "tenants:limits"}, allEntries = true)
    public void archive(UUID id, String reason) {
        Organization o = load(id);
        OrganizationStatus old = o.getStatus();
        o.setStatus(OrganizationStatus.ARCHIVED);
        subscriptionService.markStatus(o.getId(), SubscriptionStatus.CANCELLED);
        publishStatusChange(o, old, reason);
        events.publishEvent(audit(o, "ORG_ARCHIVED", Map.of("reason", reason == null ? "" : reason)));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Organization load(UUID id) {
        return orgs.findById(id).orElseThrow(() -> NotFoundException.of("entity.organization", id));
    }

    private void requireCodeAvailable(String code) {
        if (orgs.existsByCode(code)) {
            throw new ConflictException("error.data_integrity", Map.of("field", "code", "value", code));
        }
    }

    private void requireStatus(Organization o, OrganizationStatus expected) {
        if (o.getStatus() != expected) {
            throw new ValidationException("tenant.invalid_transition",
                    Map.of("from", o.getStatus().name(), "expected", expected.name()));
        }
    }

    private OrganizationStatus parseStatus(String status) {
        try {
            return OrganizationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("tenant.invalid_status", Map.of("value", status));
        }
    }

    private BillingCycle parseCycle(String cycle) {
        if (cycle == null || cycle.isBlank()) return BillingCycle.MONTHLY;
        try {
            return BillingCycle.valueOf(cycle.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("tenant.invalid_billing_cycle", Map.of("value", cycle));
        }
    }

    private UUID resolvePlanId(String planCode) {
        if (planCode == null || planCode.isBlank()) return null;
        return plans.findByCode(planCode)
                .map(SubscriptionPlan::getId)
                .orElseThrow(() -> new ValidationException("tenant.plan_not_found", Map.of("code", planCode)));
    }

    private Organization.OrganizationBuilder baseBuilder(String code, String name, String type,
                                                         String currency, String locale, String timezone,
                                                         String email, String phone, String address) {
        return Organization.builder()
                .code(code)
                .name(name)
                .type(organizationTypes.requireActiveCode(type))
                .currency(currency == null ? "MRU" : currency)
                .locale(locale == null ? "fr" : locale)
                .timezone(timezone == null ? "Africa/Nouakchott" : timezone)
                .email(email)
                .phone(phone)
                .address(address);
    }

    private void seedSettings(UUID organizationId) {
        TenantSettings ts = TenantSettings.builder()
                .organizationId(organizationId)
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
    }

    private void publishStatusChange(Organization o, OrganizationStatus old, String reason) {
        events.publishEvent(new OrganizationStatusChangedEvent(
                o.getId(), old.name(), o.getStatus().name(), reason, Instant.now()));
    }

    private OrganizationDto toDto(Organization o) {
        String planCode = o.getSubscriptionPlanId() == null ? null
                : plans.findById(o.getSubscriptionPlanId()).map(SubscriptionPlan::getCode).orElse(null);
        String subStatus = subscriptions.findByOrganizationId(o.getId())
                .map(s -> s.getStatus().name()).orElse(null);
        return new OrganizationDto(
                o.getId(), o.getCode(), o.getName(), o.getType(),
                o.getStatus().name(), o.getCurrency(), o.getLocale(), o.getTimezone(),
                o.getEmail(), o.getPhone(), o.getAddress(),
                o.getTrialEndsAt(), o.getPastDueSince(),
                planCode, subStatus, o.getPrimaryAdminUserId());
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
