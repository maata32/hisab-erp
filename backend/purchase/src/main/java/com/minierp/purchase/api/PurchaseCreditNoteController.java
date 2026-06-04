package com.minierp.purchase.api;

import com.minierp.purchase.internal.PurchaseService;
import com.minierp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-credit-notes")
@RequiredArgsConstructor
public class PurchaseCreditNoteController {

    private final PurchaseService service;

    @GetMapping
    @PreAuthorize("hasAuthority('purchase:read')")
    public PageResponse<PurchaseDto.PurchaseCreditNoteDto> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) UUID invoiceId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listPurchaseCreditNotes(supplierId, invoiceId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('purchase:read')")
    public PurchaseDto.PurchaseCreditNoteDto get(@PathVariable UUID id) {
        return service.getPurchaseCreditNote(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('purchase:read')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] bytes = service.generateCreditNotePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"purchase-credit-note-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
