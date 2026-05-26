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
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private final SalesService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales:create')")
    public SalesDto.QuoteDto create(@Valid @RequestBody SalesDto.CreateQuoteRequest req) {
        return service.createQuote(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales:read')")
    public PageResponse<SalesDto.QuoteDto> list(
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listQuotes(customerId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:read')")
    public SalesDto.QuoteDto get(@PathVariable UUID id) {
        return service.getQuote(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('sales:update')")
    public SalesDto.QuoteDto updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return service.updateQuoteStatus(id, status);
    }

    @PostMapping("/{id}/convert-to-order")
    @PreAuthorize("hasAuthority('sales:create')")
    public SalesDto.OrderDto convertToOrder(@PathVariable UUID id,
                                            @RequestParam(defaultValue = "false") boolean deliveryRequired) {
        return service.convertQuoteToOrder(id, deliveryRequired);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('sales:read')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] bytes = service.generateQuotePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"quote-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
