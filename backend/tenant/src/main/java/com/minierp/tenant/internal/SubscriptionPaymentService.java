package com.minierp.tenant.internal;

import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.error.ValidationException;
import com.minierp.tenant.api.SubscriptionPaymentDto;
import com.minierp.tenant.events.SubscriptionPaymentRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final OrganizationService organizationService;
    private final PaymentAttachmentStorageService storage;
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

    private SubscriptionPaymentDto toDto(SubscriptionPayment p) {
        return new SubscriptionPaymentDto(
                p.getId(), p.getOrganizationId(),
                p.getYears(), p.getMonths(),
                p.getAmount(), p.getCurrency(),
                p.getPaidAt(), p.getPeriodStart(), p.getPeriodEnd(),
                p.getAttachmentUrl(), p.getCreatedAt());
    }
}
