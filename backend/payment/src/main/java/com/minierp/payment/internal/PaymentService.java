package com.minierp.payment.internal;

import com.minierp.customer.api.CustomerBalanceOperations;
import com.minierp.customer.api.CustomerLookup;
import com.minierp.customer.api.CustomerSummary;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.payment.api.PaymentDto;
import com.minierp.payment.api.PaymentLookup;
import com.minierp.payment.api.StatementPaymentEntry;
import com.minierp.sales.api.InvoiceOperations;
import com.minierp.sales.api.InvoiceSummary;
import com.minierp.sales.api.NumberingOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.util.PageResponse;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService implements PaymentLookup {

    private final PaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final CustomerLookup customerLookup;
    private final CustomerBalanceOperations balanceOps;
    private final InvoiceOperations invoiceOps;
    private final NumberingOperations numbering;
    private final DocumentRenderer renderer;

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
            if (a.getTargetType() == AllocationTargetType.SALE_INVOICE) {
                invoiceOps.applyPayment(a.getTargetId(), a.getAllocatedAmount());
            } else if (a.getTargetType() == AllocationTargetType.CUSTOMER_BALANCE) {
                balanceOps.addToPaid(a.getTargetId(), a.getAllocatedAmount(), true);
            }
        }

        p.setStatus(PaymentStatus.CONFIRMED);
        p.setConfirmedAt(Instant.now());
        p.setConfirmedBy(userId);
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
                if (type == AllocationTargetType.SALE_INVOICE) {
                    invoiceOps.applyPayment(ar.targetId(), ar.allocatedAmount());
                } else if (type == AllocationTargetType.CUSTOMER_BALANCE) {
                    balanceOps.addToPaid(ar.targetId(), ar.allocatedAmount(), true);
                }
            }
        }
        return toDto(p);
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
        CustomerSummary customer = customerLookup.findById(p.getPartyId()).orElse(null);
        List<PaymentAllocation> allocs = allocations.findByPaymentId(id);
        Map<String, Object> vars = buildReceiptVars(p, allocs, customer);
        return renderer.renderPdf(PdfRenderRequest.of("payment-receipt", vars));
    }

    private PaymentDto.PaymentResponse toDto(Payment p) {
        List<PaymentAllocation> allocs = allocations.findByPaymentId(p.getId());
        String partyName = customerLookup.findById(p.getPartyId()).map(CustomerSummary::name).orElse("");
        return new PaymentDto.PaymentResponse(
                p.getId(), p.getNumber(), p.getType().name(), p.getPartyId(), partyName,
                p.getAmount(), p.getCurrency(), p.getPaymentDate(), p.getMethod().name(),
                p.getReference(), p.getStatus().name(), p.getNotes(),
                allocs.stream().map(a -> new PaymentDto.AllocationDto(
                        a.getId(), a.getTargetType().name(), a.getTargetId(),
                        a.getAllocatedAmount(), a.getNotes())).toList(),
                p.getCreatedAt());
    }

    private Map<String, Object> buildReceiptVars(Payment p, List<PaymentAllocation> allocs, CustomerSummary customer) {
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
        return Map.of(
                "payment", new PayModel(p.getNumber(), p.getPaymentDate(), p.getAmount(), p.getCurrency(),
                        p.getMethod().name(), methodLabel, p.getReference(), "CONFIRMÉ", p.getNotes(), allocModels),
                "customer", new CustModel(customer != null ? customer.name() : ""),
                "orgName", "Mini-ERP", "orgAddress", "", "logoUrl", ""
        );
    }
}
