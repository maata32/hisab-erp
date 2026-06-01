package com.minierp.allocation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Phase 2 REST surface for the unified allocation engine. The UI calls these
 * endpoints to discover allocatable items on a party and to apply a customer
 * credit against an invoice. Existing flows (payment.allocate, credit-note
 * creation) remain unchanged and will be rerouted through the engine in
 * Phase 3.
 */
@RestController
@RequestMapping("/api/v1/allocations")
@RequiredArgsConstructor
public class AllocationController {

    private final AllocationEngine engine;

    @GetMapping("/open-items")
    @PreAuthorize("hasAuthority('customer:read')")
    public List<OpenItem> openItems(@RequestParam UUID partyId) {
        return engine.findOpenItemsByParty(partyId);
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('customer:read')")
    public List<AllocationHistoryRow> history(@RequestParam UUID partyId) {
        return engine.findAllocationHistoryByParty(partyId);
    }

    @PostMapping("/credit-to-invoice")
    @PreAuthorize("hasAuthority('sales:update') and hasAuthority('customer:read')")
    public ApplyResponse applyCreditToInvoice(@Valid @RequestBody ApplyCreditToInvoiceRequest req) {
        BigDecimal consumed = engine.applyCreditToInvoice(
                req.creditId(), req.invoiceId(), req.amount());
        return new ApplyResponse(consumed);
    }

    @PostMapping("/credit-to-refund")
    @PreAuthorize("hasAuthority('payment:create') and hasAuthority('customer:read')")
    public ApplyResponse applyCreditToRefund(@Valid @RequestBody ApplyCreditToRefundRequest req) {
        BigDecimal consumed = engine.applyCreditToRefund(
                req.creditId(), req.refundPaymentId(), req.amount());
        return new ApplyResponse(consumed);
    }

    @PostMapping("/supplier-refund-to-retrait")
    @PreAuthorize("hasAuthority('payment:create') and hasAuthority('supplier:read')")
    public ApplyResponse applySupplierRefundToRetrait(@Valid @RequestBody ApplySupplierRefundToRetraitRequest req) {
        BigDecimal consumed = engine.applySupplierRefundToRetrait(
                req.refundPaymentId(), req.retraitPaymentId(), req.amount());
        return new ApplyResponse(consumed);
    }

    public record ApplyCreditToInvoiceRequest(
            @NotNull UUID creditId,
            @NotNull UUID invoiceId,
            @NotNull @Positive BigDecimal amount) {}

    public record ApplyCreditToRefundRequest(
            @NotNull UUID creditId,
            @NotNull UUID refundPaymentId,
            @NotNull @Positive BigDecimal amount) {}

    public record ApplySupplierRefundToRetraitRequest(
            @NotNull UUID refundPaymentId,
            @NotNull UUID retraitPaymentId,
            @NotNull @Positive BigDecimal amount) {}

    public record ApplyResponse(BigDecimal amountApplied) {}
}
