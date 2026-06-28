package com.hisaberp.tenant.internal;

import com.hisaberp.shared.error.NotFoundException;
import com.hisaberp.shared.error.ValidationException;
import com.hisaberp.shared.storage.StoragePresigner;
import com.hisaberp.tenant.api.SubscriptionPaymentDto;
import com.hisaberp.tenant.api.SubscriptionPaymentRow;
import com.hisaberp.tenant.api.SubscriptionRevenueDto;
import com.hisaberp.tenant.api.SubscriptionRevenueDto.Bucket;
import com.hisaberp.tenant.events.SubscriptionPaymentRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Subscription payment ledger (super-admin). Recording a payment extends the tenant's subscription
 * by {@code years + months} (via {@link OrganizationService#applyPaidPeriod}) and stores the
 * optional justification file in MinIO.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionPaymentService {

    private final SubscriptionPaymentRepository payments;
    private final OrganizationRepository orgs;
    private final SubscriptionPlanRepository plans;
    private final OrganizationService organizationService;
    private final PaymentAttachmentStorageService storage;
    private final StoragePresigner presigner;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<SubscriptionPaymentDto> list(UUID organizationId) {
        return payments.findAllByOrganizationIdOrderByPaidAtDescCreatedAtDesc(organizationId)
                .stream().map(this::toDto).toList();
    }

    @Transactional
    public SubscriptionPaymentDto record(UUID organizationId, int years, int months,
                                         BigDecimal amount, String currency, LocalDate paidAt,
                                         MultipartFile file) {
        Organization o = orgs.findById(organizationId)
                .orElseThrow(() -> NotFoundException.of("entity.organization", organizationId));
        if (years < 0 || months < 0 || (years == 0 && months == 0)) {
            throw new ValidationException("subscription_payment.invalid_duration", Map.of());
        }

        // Upload first (no DB side-effects yet), then extend the subscription + persist atomically.
        String attachmentUrl = (file != null && !file.isEmpty()) ? storage.upload(file) : null;
        OrganizationService.PaidPeriod period = organizationService.applyPaidPeriod(organizationId, years, months);

        SubscriptionPayment p = SubscriptionPayment.builder()
                .organizationId(organizationId)
                .years(years)
                .months(months)
                .amount(amount)
                .currency(currency == null || currency.isBlank() ? o.getCurrency() : currency.trim().toUpperCase())
                .paidAt(paidAt != null ? paidAt : LocalDate.now())
                .periodStart(period.start())
                .periodEnd(period.end())
                .attachmentUrl(attachmentUrl)
                .build();
        SubscriptionPayment saved = payments.save(p);

        events.publishEvent(new SubscriptionPaymentRecordedEvent(
                organizationId, o.getCode(), o.getName(), o.getEmail(), o.getName(), o.getLocale(),
                years, months, saved.getAmount(), saved.getCurrency(),
                saved.getPeriodStart(), saved.getPeriodEnd(), Instant.now()));
        return toDto(saved);
    }

    @Transactional
    public SubscriptionPaymentDto cancel(UUID paymentId) {
        SubscriptionPayment p = payments.findById(paymentId)
                .orElseThrow(() -> NotFoundException.of("entity.subscription_payment", paymentId));
        if (p.isCancelled()) {
            throw new ValidationException("subscription_payment.already_cancelled", Map.of());
        }
        p.setCancelled(true);
        p.setCancelledAt(Instant.now());
        // Roll the subscription back by this payment's duration.
        organizationService.revokePaidPeriod(p.getOrganizationId(), p.getYears(), p.getMonths());
        return toDto(p);
    }

    /** Global ledger across all tenants (super-admin), enriched with org + plan. */
    @Transactional(readOnly = true)
    public List<SubscriptionPaymentRow> listAll() {
        List<SubscriptionPayment> all = payments.findAllByOrderByPaidAtDescCreatedAtDesc();
        Map<UUID, Organization> orgMap = orgMap(
                all.stream().map(SubscriptionPayment::getOrganizationId).collect(Collectors.toSet()));
        Map<UUID, String> planCodes = planCodeMap();
        return all.stream().map(p -> toRow(p, orgMap.get(p.getOrganizationId()), planCodes)).toList();
    }

    /** Revenue breakdowns (cancelled excluded). */
    @Transactional(readOnly = true)
    public SubscriptionRevenueDto revenue() {
        List<SubscriptionPayment> all = payments.findAllByOrderByPaidAtDescCreatedAtDesc().stream()
                .filter(p -> !p.isCancelled()).toList();
        Map<UUID, Organization> orgMap = orgMap(
                all.stream().map(SubscriptionPayment::getOrganizationId).collect(Collectors.toSet()));
        Map<UUID, String> planCodes = planCodeMap();

        BigDecimal total = all.stream().map(SubscriptionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> month = new TreeMap<>();
        Map<String, BigDecimal> plan = new HashMap<>();
        Map<UUID, BigDecimal> tenant = new HashMap<>();
        for (SubscriptionPayment p : all) {
            month.merge(p.getPaidAt().toString().substring(0, 7), p.getAmount(), BigDecimal::add);
            Organization o = orgMap.get(p.getOrganizationId());
            String planCode = (o != null && o.getSubscriptionPlanId() != null)
                    ? planCodes.getOrDefault(o.getSubscriptionPlanId(), "—") : "—";
            plan.merge(planCode, p.getAmount(), BigDecimal::add);
            tenant.merge(p.getOrganizationId(), p.getAmount(), BigDecimal::add);
        }

        List<Bucket> byMonth = month.entrySet().stream()
                .map(e -> new Bucket(e.getKey(), e.getKey(), e.getValue())).toList();
        List<Bucket> byPlan = plan.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new Bucket(e.getKey(), e.getKey(), e.getValue())).toList();
        List<Bucket> byTenant = tenant.entrySet().stream()
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    Organization o = orgMap.get(e.getKey());
                    return new Bucket(e.getKey().toString(), o != null ? o.getName() : e.getKey().toString(), e.getValue());
                }).toList();

        return new SubscriptionRevenueDto(total, byMonth, byPlan, byTenant);
    }

    private Map<UUID, Organization> orgMap(Collection<UUID> orgIds) {
        Map<UUID, Organization> m = new HashMap<>();
        orgs.findAllById(orgIds).forEach(o -> m.put(o.getId(), o));
        return m;
    }

    private Map<UUID, String> planCodeMap() {
        Map<UUID, String> m = new HashMap<>();
        plans.findAll().forEach(pl -> m.put(pl.getId(), pl.getCode()));
        return m;
    }

    private SubscriptionPaymentRow toRow(SubscriptionPayment p, Organization o, Map<UUID, String> planCodes) {
        String plan = (o != null && o.getSubscriptionPlanId() != null) ? planCodes.get(o.getSubscriptionPlanId()) : null;
        return new SubscriptionPaymentRow(
                p.getId(), p.getOrganizationId(),
                o != null ? o.getCode() : null, o != null ? o.getName() : null, plan,
                p.getYears(), p.getMonths(), p.getAmount(), p.getCurrency(), p.getPaidAt(),
                p.getPeriodStart(), p.getPeriodEnd(), presigner.presign(p.getAttachmentUrl()),
                p.isCancelled(), p.getCancelledAt(), p.getCreatedAt());
    }

    private SubscriptionPaymentDto toDto(SubscriptionPayment p) {
        return new SubscriptionPaymentDto(
                p.getId(), p.getOrganizationId(),
                p.getYears(), p.getMonths(),
                p.getAmount(), p.getCurrency(),
                p.getPaidAt(), p.getPeriodStart(), p.getPeriodEnd(),
                presigner.presign(p.getAttachmentUrl()), p.isCancelled(), p.getCancelledAt(), p.getCreatedAt());
    }
}
