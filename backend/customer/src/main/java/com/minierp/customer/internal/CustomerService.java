package com.minierp.customer.internal;

import com.minierp.customer.api.*;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService implements CustomerLookup, CustomerBalanceOperations {

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
        CustomerBalance b = balances.lockByCustomerId(customerId).orElseGet(() -> getOrCreate(customerId));
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
        return balances.findByCustomerId(customerId)
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

    @Transactional(readOnly = true)
    public PageResponse<CustomerDto> list(String query, Pageable pageable) {
        if (query != null && !query.isBlank()) {
            return PageResponse.of(customers.search(query.trim(), pageable).map(this::toDto));
        }
        return PageResponse.of(customers.findByActiveTrue(pageable).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public CustomerBalanceDto getBalanceInfo(UUID id) {
        customers.findById(id).orElseThrow(() -> NotFoundException.of("entity.customer", id));
        CustomerBalance b = balances.findByCustomerId(id).orElseGet(() -> getOrCreate(id));
        return toBalanceDto(b);
    }

    @Transactional(readOnly = true)
    public List<CustomerCreditDto> listCredits(UUID id) {
        return credits.findByCustomerIdAndStatusOrderByCreatedAtAsc(id, CustomerCreditStatus.ACTIVE)
                .stream().map(this::toCreditDto).toList();
    }

    @Transactional
    public CustomerCreditDto createCredit(UUID customerId, BigDecimal amount, String source, String notes) {
        customers.findById(customerId).orElseThrow(() -> NotFoundException.of("entity.customer", customerId));
        CustomerCredit credit = CustomerCredit.builder()
                .customerId(customerId)
                .initialAmount(amount)
                .remainingAmount(amount)
                .source(CreditSource.valueOf(source))
                .notes(notes)
                .build();
        credits.save(credit);
        return toCreditDto(credit);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CustomerBalance getOrCreate(UUID customerId) {
        return balances.findByCustomerId(customerId)
                .orElse(CustomerBalance.builder().customerId(customerId).build());
    }

    private CustomerSummary toSummary(Customer c) {
        return new CustomerSummary(c.getId(), c.getCode(), c.getName(),
                c.getPhone(), c.getEmail(), c.getCurrency(), c.getCreditLimit(), c.isActive());
    }

    private CustomerDto toDto(Customer c) {
        return new CustomerDto(c.getId(), c.getCode(), c.getType().name(),
                c.getName(), c.getEmail(), c.getPhone(), c.getAddress(),
                c.getCreditLimit(), c.getCurrency(), c.getNotes(), c.isActive(),
                c.getCreatedAt());
    }

    private CustomerBalanceDto toBalanceDto(CustomerBalance b) {
        return new CustomerBalanceDto(b.getCustomerId(), b.getTotalInvoiced(),
                b.getTotalPaid(), b.getBalance(), b.getOverdueAmount(), b.getLastPaymentDate());
    }

    private CustomerCreditDto toCreditDto(CustomerCredit cr) {
        return new CustomerCreditDto(cr.getId(), cr.getCustomerId(),
                cr.getInitialAmount(), cr.getRemainingAmount(),
                cr.getSource().name(), cr.getExpiresAt(),
                cr.getStatus().name(), cr.getNotes(), cr.getCreatedAt());
    }
}
