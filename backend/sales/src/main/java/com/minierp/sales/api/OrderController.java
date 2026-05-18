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

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final SalesService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales:write')")
    public SalesDto.OrderDto create(@Valid @RequestBody SalesDto.CreateOrderRequest req) {
        return service.createOrder(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sales:read')")
    public PageResponse<SalesDto.OrderDto> list(
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return service.listOrders(customerId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:read')")
    public SalesDto.OrderDto get(@PathVariable UUID id) {
        return service.getOrder(id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('sales:write')")
    public SalesDto.OrderDto updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return service.updateOrderStatus(id, status);
    }

    @PostMapping("/{id}/convert-to-invoice")
    @PreAuthorize("hasAuthority('sales:write')")
    public SalesDto.InvoiceDto convertToInvoice(
            @PathVariable UUID id,
            @RequestParam(required = false) LocalDate dueDate,
            @RequestParam(required = false) String paymentTerms) {
        return service.convertOrderToInvoice(id, dueDate, paymentTerms);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('sales:read')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] bytes = service.generateOrderPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"order-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
