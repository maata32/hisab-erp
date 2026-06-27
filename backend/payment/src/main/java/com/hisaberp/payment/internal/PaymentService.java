package com.hisaberp.payment.internal;

import com.hisaberp.partner.api.ArBalanceOperations;
import com.hisaberp.partner.api.PartnerLookup;
import com.hisaberp.partner.api.PartnerSummary;
import com.hisaberp.partner.api.ApBalanceOperations;
import com.hisaberp.partner.api.CustomerCreditOperations;
import com.hisaberp.document.api.DocumentRenderer;
import com.hisaberp.document.api.PdfRenderRequest;
import com.hisaberp.expense.api.ExpenseOperations;
import com.hisaberp.payment.api.PaymentDto;
import com.hisaberp.payment.api.PaymentLookup;
import com.hisaberp.payment.api.StatementPaymentEntry;
import com.hisaberp.purchase.api.PurchaseInvoiceOperations;
import com.hisaberp.sales.api.InvoiceOperations;
import com.hisaberp.sales.api.InvoiceSummary;
import com.hisaberp.sales.api.NumberingOperations;
import com.hisaberp.shared.error.BusinessException;
import com.hisaberp.shared.error.NotFoundException;
import com.hisaberp.shared.persistence.TenantGuard;
import com.hisaberp.shared.tenant.TenantContext;
import com.hisaberp.shared.util.PageResponse;
import com.hisaberp.tenant.api.TenantLookup;
import com.hisaberp.treasury.api.TreasuryOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService implements PaymentLookup {

    private final PaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final PartnerLookup customerLookup;
    private final ArBalanceOperations balanceOps;
    private final PartnerLookup supplierLookup;
    private final ApBalanceOperations supplierBalanceOps;
    private final CustomerCreditOperations customerCreditOps;
    private final InvoiceOperations invoiceOps;
    private final PurchaseInvoiceOperations purchaseInvoiceOps;
    private final ExpenseOperations expenseOps;
    private final NumberingOperations numbering;
    private final DocumentRenderer renderer;
    private final TenantLookup tenantLookup;
    private final TreasuryOperations treasuryOps;
    private final org.springframework.context.ApplicationEventPublisher events;

    // ── PaymentLookup (used by the customer-statement aggregator) ──────────

    @Override
    @Transactional(readOnly = true)
    public List<StatementPaymentEntry> findConfirmedForCustomer(
            UUID customerId, LocalDate from, LocalDate to) {
        return payments.findConfirmedForCustomerStatement(customerId, from, to).stream()
                .map(p -> new StatementPaymentEntry(
                        p.getId(), p.getNumber(), p.getPaymentDate(), p.getAmount(),
                        p.getMethod().name(), p.getReference(), p.getStatus().name(),
                        p.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatementPaymentEntry> findConfirmedForSupplier(
            UUID supplierId, LocalDate from, LocalDate to) {
        return payments.findConfirmedForSupplierStatement(supplierId, from, to).stream()
                .map(p -> new StatementPaymentEntry(
                        p.getId(), p.getNumber(), p.getPaymentDate(), p.getAmount(),
                        p.getMethod().name(), p.getReference(), p.getStatus().name(),
                        p.getCreatedAt()))
                .toList();
    }

    @Transactional
    public PaymentDto.PaymentResponse create(PaymentDto.CreatePaymentRequest req) {
        requirePartyUnlessExpense(req);
        String number = numbering.nextPaymentReceiptNumber();
        Payment p = Payment.builder()
                .number(number)
                .type(PaymentType.valueOf(req.type()))
                .partyId(req.partyId())
                .amount(req.amount())
                .currency(req.currency() != null ? req.currency() : "MRU")
                .paymentDate(req.paymentDate() != null ? req.paymentDate() : LocalDate.now())
                .method(PaymentMethod.valueOf(req.method()))
                .reference(req.reference())
                .bankAccount(req.bankAccount())
                .bankAccountId(req.bankAccountId())
                .notes(req.notes())
                .status(PaymentStatus.DRAFT)
                .build();
        payments.save(p);

        if (req.allocations() != null) {
            BigDecimal totalAllocated = req.allocations().stream()
                    .map(PaymentDto.AllocationRequest::allocatedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalAllocated.compareTo(req.amount()) > 0) {
                throw new BusinessException("error.payment.over_allocated",
                        Map.of("amount", req.amount(), "allocated", totalAllocated));
            }
            for (PaymentDto.AllocationRequest ar : req.allocations()) {
                allocations.save(PaymentAllocation.builder()
                        .paymentId(p.getId())
                        .targetType(AllocationTargetType.valueOf(ar.targetType()))
                        .targetId(ar.targetId())
                        .allocatedAmount(ar.allocatedAmount())
                        .notes(ar.notes())
                        .build());
            }
        }
        return toDto(p);
    }

    @Transactional
    public PaymentDto.PaymentResponse confirm(UUID id, UUID userId) {
        Payment p = loadPaymentInTenant(id);
        if (p.getStatus() != PaymentStatus.DRAFT) {
            throw new BusinessException("error.payment.not_draft", Map.of("status", p.getStatus()));
        }

        List<PaymentAllocation> allocs = allocations.findByPaymentId(id);
        for (PaymentAllocation a : allocs) {
            applyAllocation(p, a.getTargetType(), a.getTargetId(), a.getAllocatedAmount());
        }

        p.setStatus(PaymentStatus.CONFIRMED);
        p.setConfirmedAt(Instant.now());
        p.setConfirmedBy(userId);

        recordTreasuryMovement(p, userId);

        // Phase 5: surface the just-confirmed allocations in the unified
        // engine table. Subscribers run synchronously inside this transaction
        // so the double-write either lands with the confirm or rolls back
        // together.
        events.publishEvent(new com.hisaberp.payment.api.PaymentConfirmedEvent(
                p.getId(), p.getType().name(), p.getPartyId(),
                allocs.stream()
                        .map(a -> new com.hisaberp.payment.api.PaymentConfirmedEvent.AllocationLine(
                                a.getTargetType().name(), a.getTargetId(), a.getAllocatedAmount()))
                        .toList()));

        return toDto(p);
    }

    @Transactional
    public PaymentDto.PaymentResponse cancel(UUID id) {
        Payment p = loadPaymentInTenant(id);
        if (p.getStatus() == PaymentStatus.CONFIRMED) {
            throw new BusinessException("error.payment.already_confirmed", Map.of());
        }
        p.setStatus(PaymentStatus.CANCELLED);
        return toDto(p);
    }

    /**
     * Adds new allocations to an existing payment (DRAFT or CONFIRMED). Validates
     * that cumulative allocations ≤ payment.amount. For CONFIRMED payments,
     * each new allocation is applied immediately (invoice.applyPayment or
     * customer balance addToPaid).
     */
    @Transactional
    public PaymentDto.PaymentResponse allocate(UUID paymentId, PaymentDto.AllocateRequest req) {
        Payment p = loadPaymentInTenant(paymentId);
        if (p.getStatus() == PaymentStatus.CANCELLED) {
            throw new BusinessException("error.payment.cancelled", Map.of());
        }
        if (req.allocations() == null || req.allocations().isEmpty()) {
            throw new BusinessException("error.payment.no_allocations", Map.of());
        }

        BigDecimal existing = allocations.findByPaymentId(paymentId).stream()
                .map(PaymentAllocation::getAllocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal incoming = req.allocations().stream()
                .map(PaymentDto.AllocationRequest::allocatedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (existing.add(incoming).compareTo(p.getAmount()) > 0) {
            throw new BusinessException("error.payment.over_allocated",
                    Map.of("amount", p.getAmount(), "allocated", existing.add(incoming)));
        }

        boolean applyNow = p.getStatus() == PaymentStatus.CONFIRMED;
        for (PaymentDto.AllocationRequest ar : req.allocations()) {
            AllocationTargetType type = AllocationTargetType.valueOf(ar.targetType());
            allocations.save(PaymentAllocation.builder()
                    .paymentId(paymentId)
                    .targetType(type)
                    .targetId(ar.targetId())
                    .allocatedAmount(ar.allocatedAmount())
                    .notes(ar.notes())
                    .build());
            if (applyNow) {
                applyAllocation(p, type, ar.targetId(), ar.allocatedAmount());
            }
        }
        return toDto(p);
    }

    /**
     * Dispatch a single allocation to the right downstream module based on payment type
     * (CUSTOMER party → sales invoice / customer balance, SUPPLIER party → purchase invoice
     * / supplier balance). Reads p.getType() to decide whether CUSTOMER_BALANCE means
     * customer or supplier balance for the rare PURCHASE_INVOICE / SUPPLIER_BALANCE pairing.
     */
    private void applyAllocation(Payment p, AllocationTargetType type, UUID targetId, BigDecimal amount) {
        switch (type) {
            case SALE_INVOICE -> invoiceOps.applyPayment(targetId, amount);
            case PURCHASE_INVOICE -> purchaseInvoiceOps.applyPayment(targetId, amount);
            case CUSTOMER_BALANCE -> {
                // Surplus shortcut: a cash-out to a supplier party books on the AP
                // balance, everything else on the AR balance. The party role (not
                // the type) now carries the customer/supplier nature.
                if (p.getType() == PaymentType.CASH_OUT && isSupplierParty(p.getPartyId())) {
                    supplierBalanceOps.addToPaid(targetId, amount, true);
                } else {
                    balanceOps.addToPaid(targetId, amount, true);
                }
            }
            case CUSTOMER_CREDIT -> customerCreditOps.grantCredit(targetId, amount, "OVERPAYMENT",
                    "Payment " + p.getNumber(), p.getId());
            case EXPENSE -> expenseOps.applyPayment(targetId, amount);
            default -> { /* SALE, SALARY: no-op or handled elsewhere */ }
        }
    }

    /**
     * Move the treasury on confirm so the cash actually leaves/enters the books:
     * a CASH_IN raises the balance, a CASH_OUT lowers it. CASH method hits the
     * central vault; bank/check/card/mobile hit the linked bank account (skipped
     * if none); paper settlements (CREDIT_USAGE/COMPENSATION) move nothing.
     */
    private void recordTreasuryMovement(Payment p, UUID userId) {
        BigDecimal signed = p.getType() == PaymentType.CASH_IN
                ? p.getAmount() : p.getAmount().negate();
        switch (p.getMethod()) {
            case CASH -> treasuryOps.recordVaultMovement(signed, "PAYMENT", p.getId(), userId, p.getNumber());
            case BANK_TRANSFER, CHECK, CARD, MOBILE_MONEY -> {
                if (p.getBankAccountId() != null) {
                    treasuryOps.recordBankMovement(p.getBankAccountId(), signed, "PAYMENT",
                            p.getId(), userId, p.getNumber());
                }
            }
            case CREDIT_USAGE, COMPENSATION -> { /* paper settlement — no cash/bank movement */ }
        }
    }

    /** Party-less payments are allowed only for an expense settlement (a non-empty,
     *  all-EXPENSE allocation set). Every partner payment must carry a party. */
    private void requirePartyUnlessExpense(PaymentDto.CreatePaymentRequest req) {
        if (req.partyId() != null) return;
        boolean expensePayment = req.allocations() != null && !req.allocations().isEmpty()
                && req.allocations().stream().allMatch(a -> "EXPENSE".equals(a.targetType()));
        if (!expensePayment) {
            throw new BusinessException("error.payment.party_required", Map.of());
        }
    }

    @Transactional
    public PaymentDto.PaymentResponse autoAllocate(PaymentDto.AutoAllocateRequest req) {
        Payment p = loadPaymentInTenant(req.paymentId());

        List<InvoiceSummary> unpaid = invoiceOps.findUnpaidByCustomer(req.customerId());
        BigDecimal remaining = p.getAmount();
        List<PaymentAllocation> newAllocs = new ArrayList<>();

        for (InvoiceSummary inv : unpaid) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal toApply = remaining.min(inv.balance());
            newAllocs.add(PaymentAllocation.builder()
                    .paymentId(p.getId())
                    .targetType(AllocationTargetType.SALE_INVOICE)
                    .targetId(inv.id())
                    .allocatedAmount(toApply)
                    .build());
            remaining = remaining.subtract(toApply);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            newAllocs.add(PaymentAllocation.builder()
                    .paymentId(p.getId())
                    .targetType(AllocationTargetType.CUSTOMER_BALANCE)
                    .targetId(req.customerId())
                    .allocatedAmount(remaining)
                    .notes("Unallocated surplus")
                    .build());
        }

        allocations.saveAll(newAllocs);
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public PaymentDto.PaymentResponse get(UUID id) {
        return toDto(loadPaymentInTenant(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentDto.PaymentResponse> list(UUID partyId, Pageable pageable) {
        var page = partyId != null
                ? payments.findByPartyId(partyId, pageable)
                : payments.findAll(pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public byte[] generateReceipt(UUID id) {
        Payment p = loadPaymentInTenant(id);
        List<PaymentAllocation> allocs = allocations.findByPaymentId(id);
        Map<String, Object> vars = buildReceiptVars(p, allocs);
        return renderer.renderPdf(PdfRenderRequest.of("payment-receipt", vars));
    }

    /**
     * Load a payment by id, enforcing it belongs to the current tenant. {@code findById}
     * bypasses the Hibernate tenant filter, so without this guard a token from tenant A could
     * read/modify a payment of tenant B (BUG-2 / SEC-02).
     */
    private Payment loadPaymentInTenant(UUID id) {
        return TenantGuard.requireSameTenant(payments.findById(id),
                () -> NotFoundException.of("entity.payment", id));
    }

    private PaymentDto.PaymentResponse toDto(Payment p) {
        List<PaymentAllocation> allocs = allocations.findByPaymentId(p.getId());
        String partyName = resolvePartyName(p);
        return new PaymentDto.PaymentResponse(
                p.getId(), p.getNumber(), p.getType().name(), p.getPartyId(), partyName,
                p.getAmount(), p.getCurrency(), p.getPaymentDate(), p.getMethod().name(),
                p.getReference(), p.getStatus().name(), p.getNotes(),
                allocs.stream().map(a -> new PaymentDto.AllocationDto(
                        a.getId(), a.getTargetType().name(), a.getTargetId(),
                        a.getAllocatedAmount(), a.getNotes())).toList(),
                p.getCreatedAt());
    }

    /** Party display name from the single unified partner table. Returns "" if
     *  unresolved or party-less (expense payment). */
    private String resolvePartyName(Payment p) {
        if (p.getPartyId() == null) return "";
        return customerLookup.findById(p.getPartyId())
                .map(PartnerSummary::name)
                .orElseGet(() -> supplierLookup.findById(p.getPartyId()).map(PartnerSummary::name).orElse(""));
    }

    /** True when the payment's party carries the supplier role (the customer/supplier
     *  nature is no longer encoded in the payment type). False for party-less payments. */
    private boolean isSupplierParty(UUID partyId) {
        if (partyId == null) return false;
        return customerLookup.findById(partyId)
                .map(PartnerSummary::isSupplier)
                .orElseGet(() -> supplierLookup.findById(partyId).map(PartnerSummary::isSupplier).orElse(false));
    }

    private Map<String, Object> buildReceiptVars(Payment p, List<PaymentAllocation> allocs) {
        String methodLabel = switch (p.getMethod()) {
            case CASH -> "Espèces";
            case CHECK -> "Chèque";
            case BANK_TRANSFER -> "Virement bancaire";
            case MOBILE_MONEY -> "Mobile Money";
            case CARD -> "Carte bancaire";
            case CREDIT_USAGE -> "Utilisation crédit";
            case COMPENSATION -> "Compensation";
        };
        record AllocModel(String targetLabel, BigDecimal allocatedAmount) {}
        record PayModel(String number, LocalDate paymentDate, BigDecimal amount, String currency,
                        String method, String methodLabel, String reference, String statusLabel, String notes,
                        List<AllocModel> allocations) {}
        record CustModel(String name) {}
        var allocModels = allocs.stream().map(a -> new AllocModel(
                allocTargetLabel(a), a.getAllocatedAmount())).toList();
        ReceiptLabels labels = receiptLabels(p.getType());
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("payment", new PayModel(p.getNumber(), p.getPaymentDate(), p.getAmount(), p.getCurrency(),
                p.getMethod().name(), methodLabel, p.getReference(), "CONFIRMÉ", p.getNotes(), allocModels));
        vars.put("customer", new CustModel(resolvePartyName(p)));
        vars.put("docTitle", labels.docTitle());
        vars.put("amountLabel", labels.amountLabel());
        // Party row label now comes from the party role, not the payment type.
        // A party-less payment is an expense settlement.
        vars.put("partyLabel", p.getPartyId() == null ? "Dépense"
                : (isSupplierParty(p.getPartyId()) ? "Fournisseur" : "Client"));
        return vars;
    }

    private record ReceiptLabels(String docTitle, String amountLabel) {}

    /**
     * Receipt wording keyed purely to the cash direction — a cash-in is a
     * cash-in and a cash-out is a cash-out, whatever the party. The refund
     * notion is not surfaced.
     *   CASH_IN  -> "REÇU DE PAYEMENT"    / "Montant reçu"
     *   CASH_OUT -> "BON DE DÉCAISSEMENT" / "Montant payé"
     */
    private ReceiptLabels receiptLabels(PaymentType type) {
        return switch (type) {
            case CASH_IN -> new ReceiptLabels("REÇU DE PAYEMENT", "Montant reçu");
            case CASH_OUT -> new ReceiptLabels("BON DE DÉCAISSEMENT", "Montant payé");
        };
    }

    /** Human label for an allocation target — resolves invoices to their number. */
    private String allocTargetLabel(PaymentAllocation a) {
        UUID id = a.getTargetId();
        return switch (a.getTargetType()) {
            case SALE_INVOICE -> invoiceOps.findById(id)
                    .map(s -> "Facture " + s.number()).orElse("Facture");
            case PURCHASE_INVOICE -> purchaseInvoiceOps.findById(id)
                    .map(s -> "Facture fournisseur " + s.number()).orElse("Facture fournisseur");
            case CUSTOMER_CREDIT -> "Crédit client";
            case CUSTOMER_BALANCE -> "Solde client";
            case SALE -> "Vente";
            case EXPENSE -> "Dépense";
            case SALARY -> "Salaire";
        };
    }

    private Map<String, Object> brandingVars() {
        var b = tenantLookup.findBrandingById(TenantContext.require()).orElse(null);
        Map<String, Object> m = new HashMap<>();
        m.put("orgName", b == null || b.name() == null ? "" : b.name());
        m.put("orgAddress", b == null || b.address() == null ? "" : b.address());
        m.put("orgPhone", b == null || b.phone() == null ? "" : b.phone());
        m.put("orgEmail", b == null || b.email() == null ? "" : b.email());
        m.put("logoUrl", b == null || b.logoUrl() == null ? "" : b.logoUrl());
        return m;
    }
}
