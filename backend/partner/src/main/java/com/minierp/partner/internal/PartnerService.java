package com.minierp.partner.internal;

import com.minierp.partner.api.*;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Unified service over the {@code parties} table. Handles partner CRUD across
 * both roles, partner lookup, the customer-statement aggregation hook, and the
 * activate-role workflow. Balance mutations live in {@link ArBalanceService} and
 * {@link ApBalanceService} — two beans because the interfaces have overlapping
 * method signatures and Java forbids implementing both on the same class.
 * Replaces the former CustomerService and SupplierService.
 */
@Service
@RequiredArgsConstructor
public class PartnerService implements PartnerLookup, CustomerStatementLookup {

    private final PartnerRepository partners;
    private final ArBalanceRepository arBalances;
    private final ApBalanceRepository apBalances;
    private final CustomerCreditRepository credits;
    private final CustomerCreditUsageRepository creditUsages;

    // ── PartnerLookup ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<PartnerSummary> findById(UUID id) {
        return partners.findById(id).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PartnerSummary> findByCustomerCode(String customerCode) {
        return partners.findByCustomerCode(customerCode).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PartnerSummary> findBySupplierCode(String supplierCode) {
        return partners.findBySupplierCode(supplierCode).map(this::toSummary);
    }

    // ── CustomerStatementLookup ──────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<StatementCreditEntry> findActiveCreditsForStatement(UUID customerId, LocalDate from, LocalDate to) {
        Instant fromI = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toI = to.atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC);
        return credits.findActiveForStatement(customerId, fromI, toI).stream()
                .map(c -> new StatementCreditEntry(
                        c.getId(), c.getCreatedAt(),
                        c.getInitialAmount(), c.getRemainingAmount(),
                        c.getSource().name(), c.getStatus().name(), c.getNotes()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceSnapshot getBalance(UUID customerId) {
        return arBalances.findByPartyId(customerId)
                .map(b -> new BalanceSnapshot(
                        b.getTotalInvoiced(), b.getTotalPaid(), b.getBalance(),
                        b.getOverdueAmount(), b.getLastPaymentDate()))
                .orElse(new BalanceSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, null));
    }

    // ── Partner CRUD ─────────────────────────────────────────────────────────

    @Transactional
    public PartnerDto create(CreatePartnerRequest req) {
        if (!req.isCustomer() && !req.isSupplier()) {
            throw new BusinessException("error.partner.role_required", Map.of());
        }
        if (req.isCustomer() && (req.customerCode() == null || req.customerCode().isBlank())) {
            throw new BusinessException("error.partner.customer_code_required", Map.of());
        }
        if (req.isSupplier() && (req.supplierCode() == null || req.supplierCode().isBlank())) {
            throw new BusinessException("error.partner.supplier_code_required", Map.of());
        }
        if (req.isCustomer() && partners.existsByCustomerCode(req.customerCode())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "customerCode", "value", req.customerCode()));
        }
        if (req.isSupplier() && partners.existsBySupplierCode(req.supplierCode())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "supplierCode", "value", req.supplierCode()));
        }

        Partner p = Partner.builder()
                .customerCode(req.isCustomer() ? req.customerCode() : null)
                .supplierCode(req.isSupplier() ? req.supplierCode() : null)
                .type(req.type() != null ? PartnerType.valueOf(req.type()) : PartnerType.COMPANY)
                .name(req.name())
                .email(req.email())
                .phone(req.phone())
                .address(req.address())
                .taxId(req.taxId())
                .paymentTerms(req.paymentTerms())
                .currency(req.currency() != null ? req.currency() : "MRU")
                .notes(req.notes())
                .defaultPriceTierId(req.defaultPriceTierId())
                .notificationPreferences(req.notificationPreferences())
                .customerCreditLimit(req.customerCreditLimit() != null ? req.customerCreditLimit() : BigDecimal.ZERO)
                .supplierCreditLimit(req.supplierCreditLimit() != null ? req.supplierCreditLimit() : BigDecimal.ZERO)
                .isCustomer(req.isCustomer())
                .isSupplier(req.isSupplier())
                .build();
        partners.save(p);
        if (p.isSupplier()) {
            apBalances.save(ApBalance.builder().partyId(p.getId()).build());
        }
        return toDto(p);
    }

    @Transactional
    public PartnerDto update(UUID id, CreatePartnerRequest req) {
        Partner p = partners.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.partner", id));
        p.setName(req.name());
        p.setEmail(req.email());
        p.setPhone(req.phone());
        p.setAddress(req.address());
        if (req.taxId() != null) p.setTaxId(req.taxId());
        if (req.paymentTerms() != null) p.setPaymentTerms(req.paymentTerms());
        if (req.currency() != null) p.setCurrency(req.currency());
        if (req.notes() != null) p.setNotes(req.notes());
        if (req.defaultPriceTierId() != null) p.setDefaultPriceTierId(req.defaultPriceTierId());
        if (req.notificationPreferences() != null) p.setNotificationPreferences(req.notificationPreferences());
        if (p.isCustomer() && req.customerCreditLimit() != null) p.setCustomerCreditLimit(req.customerCreditLimit());
        if (p.isSupplier() && req.supplierCreditLimit() != null) p.setSupplierCreditLimit(req.supplierCreditLimit());
        return toDto(p);
    }

    @Transactional
    public void deactivate(UUID id) {
        Partner p = partners.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.partner", id));
        p.setActive(false);
    }

    @Transactional(readOnly = true)
    public PartnerDto getById(UUID id) {
        return toDto(partners.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.partner", id)));
    }

    /**
     * Suggests the next code following {P|E}-{YY}-{NNNN} (customer) or
     * F-{YY}-{NNNN} (supplier), 4-digit zero-padded sequence per tenant/prefix.
     */
    @Transactional(readOnly = true)
    public String suggestCode(String role, String type) {
        String yy = String.format("%02d", Year.now().getValue() % 100);
        if ("SUPPLIER".equalsIgnoreCase(role)) {
            String prefix = "F-" + yy + "-";
            int next = partners.findMaxSupplierCodeByPrefix(prefix + "%")
                    .map(code -> parseSuffix(code, prefix))
                    .orElse(0) + 1;
            return prefix + String.format("%04d", next);
        }
        PartnerType t;
        try {
            t = type != null ? PartnerType.valueOf(type) : PartnerType.INDIVIDUAL;
        } catch (IllegalArgumentException ex) {
            t = PartnerType.INDIVIDUAL;
        }
        String typePrefix = t == PartnerType.COMPANY ? "E" : "P";
        String prefix = typePrefix + "-" + yy + "-";
        int next = partners.findMaxCustomerCodeByPrefix(prefix + "%")
                .map(code -> parseSuffix(code, prefix))
                .orElse(0) + 1;
        return prefix + String.format("%04d", next);
    }

    private int parseSuffix(String code, String prefix) {
        String suffix = code.substring(prefix.length());
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<PartnerDto> list(String role, String query, Pageable pageable) {
        String roleFilter = role != null && !role.isBlank() ? role.toUpperCase() : null;
        var page = (query != null && !query.isBlank())
                ? partners.searchByRole(roleFilter, query.trim(), pageable)
                : partners.findActiveByRole(roleFilter, pageable);
        var ids = page.map(Partner::getId).getContent();
        Map<UUID, BigDecimal> arByParty = arBalances.findByPartyIdIn(ids).stream()
                .collect(Collectors.toMap(ArBalance::getPartyId, ArBalance::getBalance));
        Map<UUID, BigDecimal> apByParty = apBalances.findByPartyIdIn(ids).stream()
                .collect(Collectors.toMap(ApBalance::getPartyId, ApBalance::getBalance));
        return PageResponse.of(page.map(p -> toDto(p,
                arByParty.getOrDefault(p.getId(), BigDecimal.ZERO),
                apByParty.getOrDefault(p.getId(), BigDecimal.ZERO))));
    }

    @Transactional(readOnly = true)
    public ArBalanceDto getArBalance(UUID id) {
        partners.findById(id).orElseThrow(() -> NotFoundException.of("entity.partner", id));
        ArBalance b = arBalances.findByPartyId(id)
                .orElseGet(() -> ArBalance.builder().partyId(id).build());
        return new ArBalanceDto(b.getPartyId(), b.getTotalInvoiced(),
                b.getTotalPaid(), b.getBalance(), b.getOverdueAmount(), b.getLastPaymentDate());
    }

    @Transactional(readOnly = true)
    public ApBalanceDto getApBalance(UUID id) {
        partners.findById(id).orElseThrow(() -> NotFoundException.of("entity.partner", id));
        ApBalance b = apBalances.findByPartyId(id)
                .orElseGet(() -> ApBalance.builder().partyId(id).build());
        return new ApBalanceDto(b.getPartyId(), b.getTotalInvoiced(),
                b.getTotalPaid(), b.getBalance(), b.getLastPaymentDate());
    }

    // ── Activate role workflow ───────────────────────────────────────────────

    @Transactional
    public PartnerDto activateSupplierRole(UUID id, ActivateSupplierRoleRequest req) {
        Partner p = partners.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.partner", id));
        if (p.isSupplier()) {
            throw new ConflictException("error.partner.supplier_role_already_active",
                    Map.of("partyId", id));
        }
        if (partners.existsBySupplierCode(req.supplierCode())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "supplierCode", "value", req.supplierCode()));
        }
        p.setSupplier(true);
        p.setSupplierCode(req.supplierCode());
        if (req.taxId() != null) p.setTaxId(req.taxId());
        if (req.paymentTerms() != null) p.setPaymentTerms(req.paymentTerms());
        if (req.supplierCreditLimit() != null) p.setSupplierCreditLimit(req.supplierCreditLimit());
        apBalances.save(ApBalance.builder().partyId(id).build());
        return toDto(p);
    }

    @Transactional
    public PartnerDto activateCustomerRole(UUID id, ActivateCustomerRoleRequest req) {
        Partner p = partners.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.partner", id));
        if (p.isCustomer()) {
            throw new ConflictException("error.partner.customer_role_already_active",
                    Map.of("partyId", id));
        }
        if (partners.existsByCustomerCode(req.customerCode())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "customerCode", "value", req.customerCode()));
        }
        p.setCustomer(true);
        p.setCustomerCode(req.customerCode());
        if (req.defaultPriceTierId() != null) p.setDefaultPriceTierId(req.defaultPriceTierId());
        if (req.customerCreditLimit() != null) p.setCustomerCreditLimit(req.customerCreditLimit());
        return toDto(p);
    }

    // ── Customer credits (customer-only feature) ─────────────────────────────

    @Transactional(readOnly = true)
    public List<CustomerCreditDto> listCredits(UUID id) {
        return credits.findByPartyIdAndStatusOrderByCreatedAtAsc(id, CustomerCreditStatus.ACTIVE)
                .stream().map(this::toCreditDto).toList();
    }

    @Transactional
    public CustomerCreditDto createCredit(UUID partyId, BigDecimal amount, String source, String notes) {
        Partner p = partners.findById(partyId)
                .orElseThrow(() -> NotFoundException.of("entity.partner", partyId));
        if (!p.isCustomer()) {
            throw new BusinessException("error.partner.not_a_customer", Map.of("partyId", partyId));
        }
        CustomerCredit credit = CustomerCredit.builder()
                .partyId(partyId)
                .initialAmount(amount)
                .remainingAmount(amount)
                .source(CreditSource.valueOf(source))
                .notes(notes)
                .build();
        credits.save(credit);
        return toCreditDto(credit);
    }

    @Transactional
    public CustomerCreditDto withdrawCredit(UUID partyId, UUID creditId, BigDecimal amount,
                                            UUID paymentId, String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("error.credit.invalid_amount", Map.of("amount", amount));
        }
        CustomerCredit credit = credits.findById(creditId)
                .orElseThrow(() -> NotFoundException.of("entity.customer_credit", creditId));
        if (!credit.getPartyId().equals(partyId)) {
            throw NotFoundException.of("entity.customer_credit", creditId);
        }
        if (credit.getStatus() != CustomerCreditStatus.ACTIVE) {
            throw new BusinessException("error.credit.not_active",
                    Map.of("status", credit.getStatus().name()));
        }
        if (amount.compareTo(credit.getRemainingAmount()) > 0) {
            throw new BusinessException("error.credit.insufficient_remaining",
                    Map.of("requested", amount, "remaining", credit.getRemainingAmount()));
        }
        credit.setRemainingAmount(credit.getRemainingAmount().subtract(amount));
        if (credit.getRemainingAmount().signum() <= 0) {
            credit.setStatus(CustomerCreditStatus.EXHAUSTED);
        }
        creditUsages.save(CustomerCreditUsage.builder()
                .customerCreditId(creditId)
                .paymentId(paymentId)
                .amountUsed(amount)
                .build());
        return toCreditDto(credit);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PartnerSummary toSummary(Partner p) {
        return new PartnerSummary(p.getId(), p.getCustomerCode(), p.getSupplierCode(),
                p.getType().name(), p.getName(), p.getPhone(), p.getEmail(), p.getAddress(),
                p.getCurrency(), p.getCustomerCreditLimit(), p.getSupplierCreditLimit(),
                p.getDefaultPriceTierId(), p.getPaymentTerms(), p.getTaxId(),
                p.getNotificationPreferences(), p.isCustomer(), p.isSupplier(), p.isActive());
    }

    private PartnerDto toDto(Partner p) {
        BigDecimal ar = p.isCustomer()
                ? arBalances.findByPartyId(p.getId()).map(ArBalance::getBalance).orElse(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        BigDecimal ap = p.isSupplier()
                ? apBalances.findByPartyId(p.getId()).map(ApBalance::getBalance).orElse(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        return toDto(p, ar, ap);
    }

    private PartnerDto toDto(Partner p, BigDecimal arBalance, BigDecimal apBalance) {
        return new PartnerDto(p.getId(), p.getCustomerCode(), p.getSupplierCode(),
                p.getType().name(), p.getName(), p.getEmail(), p.getPhone(), p.getAddress(),
                p.getTaxId(), p.getPaymentTerms(), p.getCurrency(), p.getNotes(),
                p.getDefaultPriceTierId(), p.getNotificationPreferences(),
                p.getCustomerCreditLimit(), p.getSupplierCreditLimit(),
                p.isCustomer(), p.isSupplier(), p.isActive(), p.getCreatedAt(),
                arBalance != null ? arBalance : BigDecimal.ZERO,
                apBalance != null ? apBalance : BigDecimal.ZERO);
    }

    private CustomerCreditDto toCreditDto(CustomerCredit cr) {
        return new CustomerCreditDto(cr.getId(), cr.getPartyId(),
                cr.getInitialAmount(), cr.getRemainingAmount(),
                cr.getSource().name(), cr.getExpiresAt(),
                cr.getStatus().name(), cr.getNotes(), cr.getCreatedAt());
    }
}
