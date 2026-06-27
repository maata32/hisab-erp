package com.hisaberp.purchase.api;

import com.hisaberp.purchase.internal.PurchaseService;
import com.hisaberp.shared.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase:create')")
    public PurchaseDto.PurchaseOrderDto create(@Valid @RequestBody PurchaseDto.CreatePurchaseOrderRequest req) {
        return service.createOrder(req);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase:read')")
    public PageResponse<PurchaseDto.PurchaseOrderDto> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listOrders(supplierId, status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:read')")
    public PurchaseDto.PurchaseOrderDto get(@PathVariable UUID id) {
        return service.getOrder(id);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('purchase:update')")
    public PurchaseDto.PurchaseOrderDto confirm(@PathVariable UUID id) {
        return service.confirmOrder(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase:update')")
    public PurchaseDto.PurchaseOrderDto cancel(@PathVariable UUID id) {
        return service.cancelOrder(id);
    }

    @PostMapping("/{id}/convert")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase:create')")
    public PurchaseDto.PurchaseInvoiceDto convert(@PathVariable UUID id,
                                                  @RequestBody(required = false) PurchaseDto.ConvertOrderToInvoiceRequest req) {
        return service.convertOrderToInvoice(id, req);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('purchase:read')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] bytes = service.generateOrderPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"purchase-order-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
