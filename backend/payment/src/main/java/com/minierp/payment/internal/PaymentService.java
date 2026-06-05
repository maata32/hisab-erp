package com.minierp.payment.internal;

import com.minierp.partner.api.ArBalanceOperations;
import com.minierp.partner.api.PartnerLookup;
import com.minierp.partner.api.PartnerSummary;
import com.minierp.partner.api.ApBalanceOperations;
import com.minierp.partner.api.CustomerCreditOperations;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.payment.api.PaymentDto;
import com.minierp.payment.api.PaymentLookup;
import com.minierp.payment.api.StatementPaymentEntry;
import com.minierp.purchase.api.PurchaseInvoiceOperations;
import com.minierp.sales.api.InvoiceOperations;
import com.minierp.sales.api.InvoiceSummary;
import com.minierp.sales.api.NumberingOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.shared.util.PageResponse;
import com.minierp.tenant.api.TenantLookup;
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
    private final NumberingOperations numbering;
    private final DocumentRenderer renderer;
    private final TenantLookup tenantLookup;
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
        Payment p = payments.findById(id).orElseThrow(() -> NotFoundException.of("entity.payment", id));
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

        // Phase 5: surface the just-confirmed allocations in the unified
        // engine table. Subscribers run synchronously inside this transaction
        // so the double-write either lands with the confirm or rolls back
        // together.
        events.publishEvent(new com.minierp.payment.api.PaymentConfirmedEvent(
                p.getId(), p.getType().name(), p.getPartyId(),
                allocs.stream()
                        .map(a -> new com.minierp.payment.api.PaymentConfirmedEvent.AllocationLine(
                                a.getTargetType().name(), a.getTargetId(), a.getAllocatedAmount()))
                        .toList()));

        return toDto(p);
    }

    @Transactional
    public PaymentDto.PaymentResponse cancel(UUID id) {
        Payment p = payments.findById(id).orElseThrow(() -> NotFoundException.of("entity.payment", id));
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
        Payment p = payments.findById(paymentId)
                .orElseThrow(() -> NotFoundException.of("entity.payment", paymentId));
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
            default -> { /* SALE, EXPENSE, SALARY: no-op or handled elsewhere */ }
        }
    }

    @Transactional
    public PaymentDto.PaymentResponse autoAllocate(PaymentDto.AutoAllocateRequest req) {
        Payment p = payments.findById(req.paymentId())
                .orElseThrow(() -> NotFoundException.of("entity.payment", req.paymentId()));

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
        return toDto(payments.findById(id).orElseThrow(() -> NotFoundException.of("entity.payment", id)));
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
        Payment p = payments.findById(id).orElseThrow(() -> NotFoundException.of("entity.payment", id));
        List<PaymentAllocation> allocs = allocations.findByPaymentId(id);
        Map<String, Object> vars = buildReceiptVars(p, allocs);
        return renderer.renderPdf(PdfRenderRequest.of("payment-receipt", vars));
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

    /** Party display name from the single unified partner table. Returns "" if unresolved. */
    private String resolvePartyName(Payment p) {
        return customerLookup.findById(p.getPartyId())
                .map(PartnerSummary::name)
                .orElseGet(() -> supplierLookup.findById(p.getPartyId()).map(PartnerSummary::name).orElse(""));
    }

    /** True when the payment's party carries the supplier role (the customer/supplier
     *  nature is no longer encoded in the payment type). */
    private boolean isSupplierParty(UUID partyId) {
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
        vars.put("partyLabel", isSupplierParty(p.getPartyId()) ? "Fournisseur" : "Client");
        return vars;
    }

    private record ReceiptLabels(String docTitle, String amountLabel) {}

    /**
     * Receipt wording keyed purely to the cash direction — a cash-in is a
     * cash-in and a cash-out is a cash-out, whatever the party. The refund
     * notion is not surfaced.
     *   CASH_IN  / CASH_IN_REFUND  -> "REÇU DE PAIEMENT" / "Montant reçu"
     *   CASH_OUT / CASH_OUT_REFUND -> "BON DE PAIEMENT"  / "Montant payé"
     */
    private ReceiptLabels receiptLabels(PaymentType type) {
        return switch (type) {
            case CASH_IN, CASH_IN_REFUND ->
                    new ReceiptLabels("REÇU DE PAIEMENT", "Montant reçu");
            case CASH_OUT, CASH_OUT_REFUND ->
                    new ReceiptLabels("BON DE PAIEMENT", "Montant payé");
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
