package com.minierp.sales.api;

import com.minierp.sales.internal.SalesService;
import com.minierp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/credit-notes")
@RequiredArgsConstructor
public class CreditNoteController {

    private final SalesService service;

    @GetMapping
    @PreAuthorize("hasAuthority('sales:read')")
    public PageResponse<SalesDto.CreditNoteDto> list(
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.listCreditNotes(customerId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sales:read')")
    public SalesDto.CreditNoteDto get(@PathVariable UUID id) {
        return service.getCreditNote(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('sales:read')")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        byte[] bytes = service.generateCreditNotePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"credit-note-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }
}
