package com.minierp.tenant.api;

import com.minierp.tenant.internal.SubscriptionPaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Cross-tenant subscription payment management (super-admin): global ledger, revenue, cancellation. */
@RestController
@RequestMapping("/api/v1/subscription-payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Subscription payments (admin)", description = "Global ledger, revenue and cancellation")
public class SubscriptionPaymentAdminController {

    private final SubscriptionPaymentService service;

    @GetMapping
    public List<SubscriptionPaymentRow> list() {
        return service.listAll();
    }

    @GetMapping("/revenue")
    public SubscriptionRevenueDto revenue() {
        return service.revenue();
    }

    @PostMapping("/{id}/cancel")
    public SubscriptionPaymentDto cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
