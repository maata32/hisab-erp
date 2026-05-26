package com.minierp.purchase.api;

import com.minierp.purchase.internal.PurchaseService;
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
@RequestMapping("/api/v1/purchase-invoices")
@RequiredArgsConstructor
public class PurchaseInvoiceController {

    private final PurchaseService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase:create')")
    public PurchaseDto.PurchaseInvoiceDto create(@Valid @RequestBody PurchaseDto.CreatePurchaseInvoiceRequest req) {
        return service.createInvoice(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase:read')")
    public PageResponse<PurchaseDto.PurchaseInvoiceDto> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listInvoices(supplierId, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:read')")
    public PurchaseDto.PurchaseInvoiceDto get(@PathVariable UUID id) {
        return service.getInvoice(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase:update')")
    public PurchaseDto.PurchaseInvoiceDto cancel(@PathVariable UUID id) {
        return service.cancelInvoice(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('purchase:read')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] bytes = service.generateInvoicePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"purchase-invoice-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
