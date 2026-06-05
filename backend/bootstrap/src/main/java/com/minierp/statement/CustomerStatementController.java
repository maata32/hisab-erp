package com.minierp.statement;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Customer statement of account — aggregates invoices (sales), credit notes (sales),
 * payments (payment), and customer credits (customer) into a single chronological
 * document. Three modes: full | detailed | outstanding. The controller lives in the
 * bootstrap module because it needs to read across module boundaries; the upstream
 * modules only expose dedicated *Statement*Lookup interfaces.
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerStatementController {

    private final CustomerStatementService service;

    @GetMapping("/{id}/statement.pdf")
    @PreAuthorize("hasAuthority('customer:read') or hasAuthority('supplier:read')")
    public ResponseEntity<byte[]> statementPdf(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "full") String type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        byte[] pdf = service.generate(id, type, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"statement-" + type + "-" + id + ".pdf\"")
                .body(pdf);
    }
}
