package com.minierp.customer.internal;

import com.minierp.customer.api.*;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService implements CustomerLookup, CustomerBalanceOperations, CustomerStatementLookup {

    private final CustomerRepository customers;
    private final CustomerBalanceRepository balances;
    private final CustomerCreditRepository credits;
    private final CustomerCreditUsageRepository creditUsages;

    // ── CustomerLookup ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSummary> findById(UUID id) {
        return customers.findById(id).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSummary> findByCode(String code) {
        return customers.findByCode(code).map(this::toSummary);
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
        return balances.findByPartyId(customerId)
                .map(b -> new BalanceSnapshot(
                        b.getTotalInvoiced(), b.getTotalPaid(), b.getBalance(),
                        b.getOverdueAmount(), b.getLastPaymentDate()))
                .orElse(new BalanceSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, null));
    }

    // ── CustomerBalanceOperations ─────────────────────────────────────────────

    @Override
    @Transactional
    public void addToInvoiced(UUID customerId, BigDecimal amount) {
        CustomerBalance b = getOrCreate(customerId);
        b.setTotalInvoiced(b.getTotalInvoiced().add(amount));
        b.setBalance(b.getTotalInvoiced().subtract(b.getTotalPaid()));
        balances.save(b);
    }

    @Override
    @Transactional
    public void addToPaid(UUID customerId, BigDecimal amount, boolean isLastPaymentToday) {
        CustomerBalance b = balances.lockByPartyId(customerId).orElseGet(() -> getOrCreate(customerId));
        b.setTotalPaid(b.getTotalPaid().add(amount));
        b.setBalance(b.getTotalInvoiced().subtract(b.getTotalPaid()));
        if (isLastPaymentToday) b.setLastPaymentDate(LocalDate.now());
        balances.save(b);
    }

    @Override
    @Transactional
    public void addToOverdue(UUID customerId, BigDecimal amount) {
        CustomerBalance b = getOrCreate(customerId);
        b.setOverdueAmount(b.getOverdueAmount().add(amount));
        balances.save(b);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalanceAmount(UUID customerId) {
        return balances.findByPartyId(customerId)
                .map(CustomerBalance::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    // ── Customer CRUD ────────────────────────────────────────────────────────

    @Transactional
    public CustomerDto create(CreateCustomerRequest req) {
        if (customers.existsByCode(req.code())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", req.code()));
        }
        Customer c = Customer.builder()
                .code(req.code())
                .type(req.type() != null ? CustomerType.valueOf(req.type()) : CustomerType.INDIVIDUAL)
                .name(req.name())
                .email(req.email())
                .phone(req.phone())
                .address(req.address())
                .creditLimit(req.creditLimit() != null ? req.creditLimit() : BigDecimal.ZERO)
                .currency(req.currency() != null ? req.currency() : "MRU")
                .notes(req.notes())
                .defaultPriceTierId(req.defaultPriceTierId())
                .notificationPreferences(req.notificationPreferences())
                .build();
        customers.save(c);
        return toDto(c);
    }

    @Transactional
    public CustomerDto update(UUID id, CreateCustomerRequest req) {
        Customer c = customers.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.customer", id));
        c.setName(req.name());
        c.setEmail(req.email());
        c.setPhone(req.phone());
        c.setAddress(req.address());
        if (req.creditLimit() != null) c.setCreditLimit(req.creditLimit());
        if (req.notes() != null) c.setNotes(req.notes());
        if (req.defaultPriceTierId() != null) c.setDefaultPriceTierId(req.defaultPriceTierId());
        if (req.notificationPreferences() != null) c.setNotificationPreferences(req.notificationPreferences());
        return toDto(c);
    }

    @Transactional
    public void deactivate(UUID id) {
        Customer c = customers.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.customer", id));
        c.setActive(false);
    }

    @Transactional(readOnly = true)
    public CustomerDto getById(UUID id) {
        return toDto(customers.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.customer", id)));
    }

    /**
     * Suggests the next customer code following the convention
     * {P|E}-{YY}-{NNNN} — prefix from CustomerType (P=INDIVIDUAL, E=BUSINESS),
     * 2-digit year of registration, 4-digit zero-padded sequence per tenant/prefix.
     * If the requested type is unknown, falls back to INDIVIDUAL.
     */
    @Transactional(readOnly = true)
    public String suggestCode(String type) {
        CustomerType t;
        try {
            t = type != null ? CustomerType.valueOf(type) : CustomerType.INDIVIDUAL;
        } catch (IllegalArgumentException ex) {
            t = CustomerType.INDIVIDUAL;
        }
        String typePrefix = t == CustomerType.COMPANY ? "E" : "P";
        String yy = String.format("%02d", Year.now().getValue() % 100);
        String prefix = typePrefix + "-" + yy + "-";
        int next = customers.findMaxCodeByPrefix(prefix + "%")
                .map(code -> {
                    String suffix = code.substring(prefix.length());
                    try {
                        return Integer.parseInt(suffix);
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .orElse(0) + 1;
        return prefix + String.format("%04d", next);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerDto> list(String query, Pageable pageable) {
        var page = (query != null && !query.isBlank())
                ? customers.search(query.trim(), pageable)
                : customers.findByActiveTrue(pageable);
        Map<UUID, BigDecimal> balanceByCustomer = balances
                .findByPartyIdIn(page.map(Customer::getId).getContent())
                .stream()
                .collect(Collectors.toMap(CustomerBalance::getPartyId, CustomerBalance::getBalance));
        return PageResponse.of(page.map(c -> toDto(c, balanceByCustomer.getOrDefault(c.getId(), BigDecimal.ZERO))));
    }

    @Transactional(readOnly = true)
    public CustomerBalanceDto getBalanceInfo(UUID id) {
        customers.findById(id).orElseThrow(() -> NotFoundException.of("entity.customer", id));
        CustomerBalance b = balances.findByPartyId(id).orElseGet(() -> getOrCreate(id));
        return toBalanceDto(b);
    }

    @Transactional(readOnly = true)
    public List<CustomerCreditDto> listCredits(UUID id) {
        return credits.findByPartyIdAndStatusOrderByCreatedAtAsc(id, CustomerCreditStatus.ACTIVE)
                .stream().map(this::toCreditDto).toList();
    }

    @Transactional
    public CustomerCreditDto createCredit(UUID customerId, BigDecimal amount, String source, String notes) {
        customers.findById(customerId).orElseThrow(() -> NotFoundException.of("entity.customer", customerId));
        CustomerCredit credit = CustomerCredit.builder()
                .partyId(customerId)
                .initialAmount(amount)
                .remainingAmount(amount)
                .source(CreditSource.valueOf(source))
                .notes(notes)
                .build();
        credits.save(credit);
        return toCreditDto(credit);
    }

    /**
     * CDC §3.6.3 Case 4 — withdraw from a customer credit to settle a future allocation.
     * Decrements remainingAmount; if it hits zero, marks the credit EXHAUSTED.
     * Records a CustomerCreditUsage row (paymentId may be null for manual withdrawals).
     */
    @Transactional
    public CustomerCreditDto withdrawCredit(UUID customerId, UUID creditId, BigDecimal amount,
                                            UUID paymentId, String notes) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("error.credit.invalid_amount", Map.of("amount", amount));
        }
        CustomerCredit credit = credits.findById(creditId)
                .orElseThrow(() -> NotFoundException.of("entity.customer_credit", creditId));
        if (!credit.getPartyId().equals(customerId)) {
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

    private CustomerBalance getOrCreate(UUID customerId) {
        return balances.findByPartyId(customerId)
                .orElse(CustomerBalance.builder().partyId(customerId).build());
    }

    private CustomerSummary toSummary(Customer c) {
        return new CustomerSummary(c.getId(), c.getCode(), c.getName(),
                c.getPhone(), c.getEmail(), c.getCurrency(), c.getCreditLimit(),
                c.getDefaultPriceTierId(), c.getNotificationPreferences(),
                c.isActive());
    }

    private CustomerDto toDto(Customer c) {
        return toDto(c, balances.findByPartyId(c.getId())
                .map(CustomerBalance::getBalance)
                .orElse(BigDecimal.ZERO));
    }

    private CustomerDto toDto(Customer c, BigDecimal balance) {
        return new CustomerDto(c.getId(), c.getCode(), c.getType().name(),
                c.getName(), c.getEmail(), c.getPhone(), c.getAddress(),
                c.getCreditLimit(), c.getCurrency(), c.getNotes(),
                c.getDefaultPriceTierId(), c.getNotificationPreferences(),
                c.isActive(), c.getCreatedAt(),
                balance != null ? balance : BigDecimal.ZERO);
    }

    private CustomerBalanceDto toBalanceDto(CustomerBalance b) {
        return new CustomerBalanceDto(b.getPartyId(), b.getTotalInvoiced(),
                b.getTotalPaid(), b.getBalance(), b.getOverdueAmount(), b.getLastPaymentDate());
    }

    private CustomerCreditDto toCreditDto(CustomerCredit cr) {
        return new CustomerCreditDto(cr.getId(), cr.getPartyId(),
                cr.getInitialAmount(), cr.getRemainingAmount(),
                cr.getSource().name(), cr.getExpiresAt(),
                cr.getStatus().name(), cr.getNotes(), cr.getCreatedAt());
    }
}
