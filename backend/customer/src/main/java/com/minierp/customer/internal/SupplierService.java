package com.minierp.customer.internal;

import com.minierp.customer.api.ActivateCustomerRoleRequest;
import com.minierp.customer.api.CreateSupplierRequest;
import com.minierp.customer.api.SupplierBalanceDto;
import com.minierp.customer.api.SupplierBalanceOperations;
import com.minierp.customer.api.SupplierDto;
import com.minierp.customer.api.SupplierLookup;
import com.minierp.customer.api.SupplierSummary;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.util.PageResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierService implements SupplierLookup, SupplierBalanceOperations {

    private final SupplierRepository suppliers;
    private final SupplierBalanceRepository balances;
    private final CustomerBalanceRepository customerBalances;

    @PersistenceContext
    private EntityManager em;

    // ── SupplierLookup ───────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplierSummary> findById(UUID id) {
        return suppliers.findById(id).map(this::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplierSummary> findByCode(String code) {
        return suppliers.findByCode(code).map(this::toSummary);
    }

    // ── SupplierBalanceOperations ────────────────────────────────────────────

    @Override
    @Transactional
    public void addToInvoiced(UUID supplierId, BigDecimal amount) {
        SupplierBalance b = getOrCreate(supplierId);
        b.setTotalInvoiced(b.getTotalInvoiced().add(amount));
        b.setBalance(b.getTotalInvoiced().subtract(b.getTotalPaid()));
        balances.save(b);
    }

    @Override
    @Transactional
    public void addToPaid(UUID supplierId, BigDecimal amount, boolean isLastPaymentToday) {
        SupplierBalance b = balances.lockByPartyId(supplierId).orElseGet(() -> getOrCreate(supplierId));
        b.setTotalPaid(b.getTotalPaid().add(amount));
        b.setBalance(b.getTotalInvoiced().subtract(b.getTotalPaid()));
        if (isLastPaymentToday) b.setLastPaymentDate(LocalDate.now());
        balances.save(b);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalanceAmount(UUID supplierId) {
        return balances.findByPartyId(supplierId)
                .map(SupplierBalance::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    // ── Supplier CRUD ────────────────────────────────────────────────────────

    @Transactional
    public SupplierDto create(CreateSupplierRequest req) {
        if (suppliers.existsByCode(req.code())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", req.code()));
        }
        Supplier s = Supplier.builder()
                .code(req.code())
                .type(req.type() != null ? CustomerType.valueOf(req.type()) : CustomerType.COMPANY)
                .name(req.name())
                .email(req.email())
                .phone(req.phone())
                .address(req.address())
                .taxId(req.taxId())
                .paymentTerms(req.paymentTerms())
                .currency(req.currency() != null ? req.currency() : "MRU")
                .notes(req.notes())
                .creditLimit(req.creditLimit() != null ? req.creditLimit() : BigDecimal.ZERO)
                .build();
        suppliers.save(s);
        return toDto(s, BigDecimal.ZERO);
    }

    @Transactional
    public SupplierDto update(UUID id, CreateSupplierRequest req) {
        Supplier s = suppliers.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.supplier", id));
        s.setName(req.name());
        s.setEmail(req.email());
        s.setPhone(req.phone());
        s.setAddress(req.address());
        if (req.taxId() != null) s.setTaxId(req.taxId());
        if (req.paymentTerms() != null) s.setPaymentTerms(req.paymentTerms());
        if (req.currency() != null) s.setCurrency(req.currency());
        if (req.notes() != null) s.setNotes(req.notes());
        if (req.creditLimit() != null) s.setCreditLimit(req.creditLimit());
        return toDto(s);
    }

    @Transactional
    public void deactivate(UUID id) {
        Supplier s = suppliers.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.supplier", id));
        s.setActive(false);
    }

    @Transactional(readOnly = true)
    public SupplierDto getById(UUID id) {
        return toDto(suppliers.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.supplier", id)));
    }

    @Transactional(readOnly = true)
    public String suggestCode() {
        String yy = String.format("%02d", Year.now().getValue() % 100);
        String prefix = "F-" + yy + "-";
        int next = suppliers.findMaxCodeByPrefix(prefix + "%")
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
    public PageResponse<SupplierDto> list(String query, Pageable pageable) {
        var page = (query != null && !query.isBlank())
                ? suppliers.search(query.trim(), pageable)
                : suppliers.findByActiveTrue(pageable);
        Map<UUID, BigDecimal> balanceBySupplier = balances
                .findByPartyIdIn(page.map(Supplier::getId).getContent())
                .stream()
                .collect(Collectors.toMap(SupplierBalance::getPartyId, SupplierBalance::getBalance));
        return PageResponse.of(page.map(s -> toDto(s, balanceBySupplier.getOrDefault(s.getId(), BigDecimal.ZERO))));
    }

    @Transactional(readOnly = true)
    public SupplierBalanceDto getBalanceInfo(UUID id) {
        suppliers.findById(id).orElseThrow(() -> NotFoundException.of("entity.supplier", id));
        SupplierBalance b = balances.findByPartyId(id).orElseGet(() -> getOrCreate(id));
        return new SupplierBalanceDto(b.getPartyId(), b.getTotalInvoiced(),
                b.getTotalPaid(), b.getBalance(), b.getLastPaymentDate());
    }

    /**
     * Promotes a supplier party to also be a customer (dual-role). Flips
     * {@code is_customer=true} on the parties row, populates the customer-side
     * columns, and seeds an empty {@code ar_balances} row.
     */
    @Transactional
    public SupplierDto activateCustomerRole(UUID id, ActivateCustomerRoleRequest req) {
        Supplier s = suppliers.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.supplier", id));
        if (s.isAlsoCustomer()) {
            throw new ConflictException("error.party.customer_role_already_active",
                    Map.of("partyId", id));
        }
        Long dupes = (Long) em.createNativeQuery(
                "SELECT COUNT(*) FROM parties WHERE customer_code = :code")
                .setParameter("code", req.customerCode())
                .getSingleResult();
        if (dupes != null && dupes > 0) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "customerCode", "value", req.customerCode()));
        }
        em.createNativeQuery(
                "UPDATE parties SET is_customer = true, customer_code = :code, " +
                        "default_price_tier_id = COALESCE(:tier, default_price_tier_id), " +
                        "customer_credit_limit = COALESCE(:limit, customer_credit_limit) " +
                        "WHERE id = :id")
                .setParameter("code", req.customerCode())
                .setParameter("tier", req.defaultPriceTierId())
                .setParameter("limit", req.customerCreditLimit())
                .setParameter("id", id)
                .executeUpdate();
        customerBalances.save(CustomerBalance.builder().partyId(id).build());
        em.refresh(s);
        return toDto(s);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SupplierBalance getOrCreate(UUID supplierId) {
        return balances.findByPartyId(supplierId)
                .orElse(SupplierBalance.builder().partyId(supplierId).build());
    }

    private SupplierSummary toSummary(Supplier s) {
        return new SupplierSummary(s.getId(), s.getCode(), s.getName(),
                s.getPhone(), s.getEmail(), s.getCurrency(), s.getPaymentTerms(), s.isActive());
    }

    private SupplierDto toDto(Supplier s) {
        return toDto(s, balances.findByPartyId(s.getId())
                .map(SupplierBalance::getBalance)
                .orElse(BigDecimal.ZERO));
    }

    private SupplierDto toDto(Supplier s, BigDecimal balance) {
        return new SupplierDto(s.getId(), s.getCode(), s.getType().name(),
                s.getName(), s.getEmail(), s.getPhone(), s.getAddress(),
                s.getTaxId(), s.getPaymentTerms(), s.getCurrency(), s.getNotes(),
                s.getCreditLimit(), s.isActive(), s.getCreatedAt(),
                balance != null ? balance : BigDecimal.ZERO,
                s.isAlsoCustomer(), s.getCustomerCode());
    }
}
