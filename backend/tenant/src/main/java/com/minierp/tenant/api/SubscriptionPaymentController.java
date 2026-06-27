package com.minierp.tenant.api;

import com.minierp.tenant.internal.SubscriptionPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Super-admin ledger of a tenant's subscription payments. Recording one extends the subscription. */
@RestController
@RequestMapping("/api/v1/organizations/{orgId}/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Subscription payments", description = "Tenant subscription payment ledger (super-admin)")
public class SubscriptionPaymentController {

    private final SubscriptionPaymentService service;

    @GetMapping
    public List<SubscriptionPaymentDto> list(@PathVariable UUID orgId) {
        return service.list(orgId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubscriptionPaymentDto record(
            @PathVariable UUID orgId,
            @RequestParam int years,
            @RequestParam int months,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidAt,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return service.record(orgId, years, months, amount, currency, paidAt, file);
    }
}
