package com.minierp.customer.internal;

import com.minierp.customer.api.*;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
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
import java.util.HashMap;
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
    private final DocumentRenderer pdfRenderer;

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
        if (!credit.getCustomerId().equals(customerId)) {
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

    /**
     * CDC §15.4 GET /customers/{id}/statement.pdf — periodic statement of account
     * (balance, recent payments, open credits). Rendered by Thymeleaf + OpenHTMLtoPDF.
     */
    @Transactional(readOnly = true)
    public byte[] generateStatementPdf(UUID customerId) {
        Customer c = customers.findById(customerId)
                .orElseThrow(() -> NotFoundException.of("entity.customer", customerId));
        CustomerBalance b = balances.findByCustomerId(customerId).orElseGet(() -> getOrCreate(customerId));
        List<CustomerCredit> openCredits = credits
                .findByCustomerIdAndStatusOrderByCreatedAtAsc(customerId, CustomerCreditStatus.ACTIVE);

        Map<String, Object> vars = new HashMap<>();
        vars.put("customer", Map.of(
                "id", c.getId(),
                "code", c.getCode(),
                "name", c.getName(),
                "email", c.getEmail() == null ? "" : c.getEmail(),
                "phone", c.getPhone() == null ? "" : c.getPhone(),
                "address", c.getAddress() == null ? "" : c.getAddress()
        ));
        vars.put("balance", Map.of(
                "totalInvoiced", b.getTotalInvoiced(),
                "totalPaid", b.getTotalPaid(),
                "balance", b.getBalance(),
                "overdue", b.getOverdueAmount(),
                "lastPaymentDate", b.getLastPaymentDate() == null ? "" : b.getLastPaymentDate().toString()
        ));
        vars.put("credits", openCredits.stream().map(cr -> Map.of(
                "createdAt", cr.getCreatedAt(),
                "initialAmount", cr.getInitialAmount(),
                "remainingAmount", cr.getRemainingAmount(),
                "source", cr.getSource().name()
        )).toList());
        vars.put("statementDate", LocalDate.now());
        vars.put("currency", c.getCurrency());
        return pdfRenderer.renderPdf(PdfRenderRequest.of("customer-statement", vars));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CustomerBalance getOrCreate(UUID customerId) {
        return balances.findByCustomerId(customerId)
                .orElse(CustomerBalance.builder().customerId(customerId).build());
    }

    private CustomerSummary toSummary(Customer c) {
        return new CustomerSummary(c.getId(), c.getCode(), c.getName(),
                c.getPhone(), c.getEmail(), c.getCurrency(), c.getCreditLimit(),
                c.getDefaultPriceTierId(), c.getNotificationPreferences(),
                c.isActive());
    }

    private CustomerDto toDto(Customer c) {
        return new CustomerDto(c.getId(), c.getCode(), c.getType().name(),
                c.getName(), c.getEmail(), c.getPhone(), c.getAddress(),
                c.getCreditLimit(), c.getCurrency(), c.getNotes(),
                c.getDefaultPriceTierId(), c.getNotificationPreferences(),
                c.isActive(), c.getCreatedAt());
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
