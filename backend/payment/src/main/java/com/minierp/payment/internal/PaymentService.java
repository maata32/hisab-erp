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
                        p.getMethod().name(), p.getReference(), p.getStatus().name()))
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

    @Transactional(readOnly = true)
    public PaymentDto.RefundPreviewResponse refundPreview(UUID id) {
        Payment p = payments.findById(id).orElseThrow(() -> NotFoundException.of("entity.payment", id));
        ensureRefundable(p);

        List<PaymentAllocation> allocs = allocations.findByPaymentId(id);
        BigDecimal revokable = customerCreditOps.sumRevokableCreditsBySourcePayment(p.getId());
        List<PaymentDto.RefundImpactRow> rows = new ArrayList<>();
        for (PaymentAllocation a : allocs) {
            rows.add(buildImpactRow(p, a.getTargetType(), a.getTargetId(), a.getAllocatedAmount(), revokable));
        }
        return new PaymentDto.RefundPreviewResponse(
                p.getId(), p.getNumber(), p.getAmount(), p.getCurrency(),
                p.getPartyId(), resolvePartyName(p),
                revokable,
                rows);
    }

    @Transactional
    public PaymentDto.PaymentResponse refund(UUID id, PaymentDto.RefundRequest req, UUID userId) {
        Payment original = payments.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.payment", id));
        ensureRefundable(original);

        // Reverse every allocation that mutated downstream state.
        List<PaymentAllocation> allocs = allocations.findByPaymentId(id);
        for (PaymentAllocation a : allocs) {
            reverseAllocation(original, a.getTargetType(), a.getTargetId(), a.getAllocatedAmount());
        }
        // Bulk-cancel any still-active credits the original payment created.
        customerCreditOps.revokeCreditsBySourcePayment(original.getId(),
                "Refund " + (req.reason() == null ? "" : req.reason()).trim());

        // Mint the new refund-payment document so the cash-out is materialized.
        PaymentType refundType = switch (original.getType()) {
            case SUPPLIER_PAYMENT -> PaymentType.SUPPLIER_REFUND;
            default -> PaymentType.CUSTOMER_REFUND;
        };
        String refundNumber = numbering.nextPaymentReceiptNumber();
        String notes = "Remboursement de " + original.getNumber()
                + (req.reason() != null && !req.reason().isBlank() ? " — " + req.reason() : "");
        Payment refund = Payment.builder()
                .number(refundNumber)
                .type(refundType)
                .partyId(original.getPartyId())
                .amount(original.getAmount())
                .currency(original.getCurrency())
                .paymentDate(req.paymentDate() != null ? req.paymentDate() : LocalDate.now())
                .method(PaymentMethod.valueOf(req.method()))
                .reference(req.reference())
                .notes(notes)
                .status(PaymentStatus.CONFIRMED)
                .confirmedAt(Instant.now())
                .confirmedBy(userId)
                .build();
        payments.save(refund);

        // Mark the original.
        original.setStatus(PaymentStatus.REFUNDED);
        original.setRefundedAt(Instant.now());
        original.setRefundedByPaymentId(refund.getId());

        // Close the loop on the unified engine: the confirm mirrored this
        // payment's invoice allocations into the allocations audit table — now
        // soft-void them so it stops claiming the un-paid invoices were settled
        // by this payment. Subscriber runs synchronously inside this transaction
        // (Propagation.MANDATORY) so the reversal commits or rolls back with the
        // refund.
        events.publishEvent(new com.minierp.payment.api.PaymentRefundedEvent(
                original.getId(), original.getType().name(), original.getPartyId(),
                refund.getId(), refund.getNumber(), userId, req.reason()));

        return toDto(refund);
    }

    private void ensureRefundable(Payment p) {
        if (p.getStatus() == PaymentStatus.REFUNDED) {
            throw new BusinessException("error.payment.already_refunded", Map.of());
        }
        if (p.getStatus() != PaymentStatus.CONFIRMED) {
            throw new BusinessException("error.payment.refund_not_confirmed",
                    Map.of("status", p.getStatus()));
        }
        if (p.getType() == PaymentType.CUSTOMER_REFUND || p.getType() == PaymentType.SUPPLIER_REFUND) {
            throw new BusinessException("error.payment.refund_of_refund", Map.of());
        }
    }

    private PaymentDto.RefundImpactRow buildImpactRow(Payment p, AllocationTargetType type,
                                                     UUID targetId, BigDecimal amount,
                                                     BigDecimal revokableTotal) {
        return switch (type) {
            case SALE_INVOICE -> {
                var inv = invoiceOps.findById(targetId).orElse(null);
                String label = inv != null ? inv.number() : targetId.toString().substring(0, 8);
                BigDecimal newPaid = inv == null ? BigDecimal.ZERO : inv.paidAmount().subtract(amount).max(BigDecimal.ZERO);
                String afterStatus = inv == null ? "?"
                        : (newPaid.signum() == 0 ? "ISSUED"
                            : (inv.balance().add(amount).signum() == 0 ? "PAID" : "PARTIAL"));
                yield new PaymentDto.RefundImpactRow(type.name(), targetId, "Facture " + label, amount, afterStatus);
            }
            case PURCHASE_INVOICE -> {
                var inv = purchaseInvoiceOps.findById(targetId).orElse(null);
                String label = inv != null ? inv.number() : targetId.toString().substring(0, 8);
                yield new PaymentDto.RefundImpactRow(type.name(), targetId, "Facture achat " + label, amount, "ISSUED/PARTIAL");
            }
            case CUSTOMER_BALANCE -> new PaymentDto.RefundImpactRow(type.name(), targetId,
                    p.getType() == PaymentType.SUPPLIER_PAYMENT ? "Solde fournisseur" : "Solde client",
                    amount, "");
            case CUSTOMER_CREDIT -> new PaymentDto.RefundImpactRow(type.name(), targetId,
                    "Crédit client (révoqué : " + revokableTotal + " sur " + amount + ")",
                    amount, "CANCELLED");
            default -> new PaymentDto.RefundImpactRow(type.name(), targetId, type.name(), amount, "");
        };
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
                if (p.getType() == PaymentType.SUPPLIER_PAYMENT) {
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

    /**
     * Reverse a single allocation when refunding. Mirrors {@link #applyAllocation}.
     * CUSTOMER_CREDIT is handled in bulk by {@link #refund} via
     * {@code revokeCreditsBySourcePayment}, so this method skips it.
     */
    private void reverseAllocation(Payment p, AllocationTargetType type, UUID targetId, BigDecimal amount) {
        switch (type) {
            case SALE_INVOICE -> invoiceOps.reversePayment(targetId, amount);
            case PURCHASE_INVOICE -> purchaseInvoiceOps.reversePayment(targetId, amount);
            case CUSTOMER_BALANCE -> {
                if (p.getType() == PaymentType.SUPPLIER_PAYMENT) {
                    supplierBalanceOps.addToPaid(targetId, amount.negate(), false);
                } else {
                    balanceOps.addToPaid(targetId, amount.negate(), false);
                }
            }
            case CUSTOMER_CREDIT -> { /* revoked by revokeCreditsBySourcePayment in refund() */ }
            default -> { /* SALE, EXPENSE, SALARY */ }
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
        PartnerSummary customer = customerLookup.findById(p.getPartyId()).orElse(null);
        List<PaymentAllocation> allocs = allocations.findByPaymentId(id);
        Map<String, Object> vars = buildReceiptVars(p, allocs, customer);
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

    /**
     * Resolves the party display name. For SUPPLIER_* payment types, looks up the supplier
     * first; otherwise falls back to the customer lookup. Returns "" if neither resolves.
     */
    private String resolvePartyName(Payment p) {
        if (p.getType() == PaymentType.SUPPLIER_PAYMENT || p.getType() == PaymentType.SUPPLIER_REFUND) {
            return supplierLookup.findById(p.getPartyId())
                    .map(PartnerSummary::name)
                    .orElseGet(() -> customerLookup.findById(p.getPartyId()).map(PartnerSummary::name).orElse(""));
        }
        return customerLookup.findById(p.getPartyId())
                .map(PartnerSummary::name)
                .orElseGet(() -> supplierLookup.findById(p.getPartyId()).map(PartnerSummary::name).orElse(""));
    }

    private Map<String, Object> buildReceiptVars(Payment p, List<PaymentAllocation> allocs, PartnerSummary customer) {
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
                a.getTargetType().name() + " " + a.getTargetId().toString().substring(0, 8),
                a.getAllocatedAmount())).toList();
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("payment", new PayModel(p.getNumber(), p.getPaymentDate(), p.getAmount(), p.getCurrency(),
                p.getMethod().name(), methodLabel, p.getReference(), "CONFIRMÉ", p.getNotes(), allocModels));
        vars.put("customer", new CustModel(customer != null ? customer.name() : ""));
        return vars;
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
