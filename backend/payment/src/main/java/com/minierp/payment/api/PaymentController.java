package com.minierp.payment.api;

import com.minierp.payment.internal.PaymentService;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.util.PageResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('payment:write')")
    public PaymentDto.PaymentResponse create(@Valid @RequestBody PaymentDto.CreatePaymentRequest req) {
        return service.create(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('payment:read')")
    public PageResponse<PaymentDto.PaymentResponse> list(
            @RequestParam(required = false) UUID partyId,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.list(partyId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payment:read')")
    public PaymentDto.PaymentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('payment:write')")
    public PaymentDto.PaymentResponse confirm(@PathVariable UUID id) {
        return service.confirm(id, CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('payment:write')")
    public PaymentDto.PaymentResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @PostMapping("/auto-allocate")
    @PreAuthorize("hasAuthority('payment:write')")
    public PaymentDto.PaymentResponse autoAllocate(@Valid @RequestBody PaymentDto.AutoAllocateRequest req) {
        return service.autoAllocate(req);
    }

    /**
     * Manual post-hoc allocation: distribute the still-unallocated portion of an
     * existing payment across invoices and/or customer credit. Works on DRAFT and
     * CONFIRMED payments; the latter applies the new allocations immediately.
     */
    @PostMapping("/{id}/allocate")
    @PreAuthorize("hasAuthority('payment:write')")
    public PaymentDto.PaymentResponse allocate(@PathVariable UUID id,
                                               @Valid @RequestBody PaymentDto.AllocateRequest req) {
        return service.allocate(id, req);
    }

    @GetMapping("/{id}/receipt.pdf")
    @PreAuthorize("hasAuthority('payment:read')")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
        byte[] bytes = service.generateReceipt(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"receipt-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
