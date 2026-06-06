package com.minierp.purchase.api;

import com.minierp.purchase.internal.GoodsReceiptService;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.util.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/goods-receipts")
@RequiredArgsConstructor
public class GoodsReceiptController {

    private final GoodsReceiptService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase:create')")
    public GoodsReceiptDto.GoodsReceiptResponse create(@Valid @RequestBody GoodsReceiptDto.CreateGoodsReceiptRequest req) {
        return service.create(req, currentUserId());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('purchase:read')")
    public PageResponse<GoodsReceiptDto.GoodsReceiptResponse> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID invoiceId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(supplierId, invoiceId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:read')")
    public GoodsReceiptDto.GoodsReceiptResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    /** Outstanding reception lines for an invoice (product + remaining qty) used to
     *  prefill the reception create dialog, mirroring the sales BL dialog. */
    @GetMapping("/outstanding-lines")
    @PreAuthorize("hasAuthority('purchase:read')")
    public java.util.List<GoodsReceiptDto.LineRequest> outstandingLines(@RequestParam UUID invoiceId) {
        return service.outstandingLines(invoiceId);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('purchase:receive')")
    public GoodsReceiptDto.GoodsReceiptResponse start(@PathVariable UUID id) {
        return service.startReceipt(id, currentUserId());
    }

    @PostMapping("/{id}/record")
    @PreAuthorize("hasAuthority('purchase:receive')")
    public GoodsReceiptDto.GoodsReceiptResponse record(@PathVariable UUID id,
                                                       @Valid @RequestBody GoodsReceiptDto.RecordReceiptRequest req) {
        return service.recordReceipt(id, req, currentUserId());
    }

    @PostMapping("/immediate")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('purchase:receive')")
    public GoodsReceiptDto.GoodsReceiptResponse immediate(@RequestParam UUID invoiceId,
                                                          @RequestParam(required = false) UUID warehouseId) {
        return service.receiveImmediately(invoiceId, warehouseId, currentUserId());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('purchase:update')")
    public GoodsReceiptDto.GoodsReceiptResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('purchase:read')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] bytes = service.generatePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"goods-receipt-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    private UUID currentUserId() {
        return CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
    }
}
