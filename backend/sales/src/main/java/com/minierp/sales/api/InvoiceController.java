package com.minierp.sales.api;

import com.minierp.sales.internal.SalesService;
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
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final SalesService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales:write')")
    public SalesDto.InvoiceDto create(@Valid @RequestBody SalesDto.CreateInvoiceRequest req) {
        return service.createInvoice(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales:read')")
    public PageResponse<SalesDto.InvoiceDto> list(
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listInvoices(customerId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:read')")
    public SalesDto.InvoiceDto get(@PathVariable UUID id) {
        return service.getInvoice(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sales:write')")
    public SalesDto.InvoiceDto cancel(@PathVariable UUID id) {
        return service.cancelInvoice(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('sales:read')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] bytes = service.generateInvoicePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"invoice-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @PostMapping("/{id}/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales:write')")
    public SalesDto.CreditNoteDto createCreditNote(@PathVariable UUID id,
                                                   @Valid @RequestBody SalesDto.CreateCreditNoteRequest req) {
        return service.createCreditNote(new SalesDto.CreateCreditNoteRequest(id, req.reason(), req.amount()));
    }

    @GetMapping("/credit-notes")
    @PreAuthorize("hasAuthority('sales:read')")
    public PageResponse<SalesDto.CreditNoteDto> listCreditNotes(
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listCreditNotes(customerId, pageable);
    }
}
