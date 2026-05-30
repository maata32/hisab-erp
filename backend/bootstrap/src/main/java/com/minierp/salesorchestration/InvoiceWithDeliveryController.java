package com.minierp.salesorchestration;

import com.minierp.sales.api.SalesDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceWithDeliveryController {

    private final InvoiceWithDeliveryService service;

    @PostMapping("/with-immediate-delivery")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sales:create') and hasAuthority('delivery:create')")
    public SalesDto.InvoiceDto createAndShip(@Valid @RequestBody Request req) {
        return service.createAndShip(req.invoice(), req.warehouseId());
    }

    public record Request(
            @Valid @NotNull SalesDto.CreateInvoiceRequest invoice,
            @NotNull UUID warehouseId
    ) {}
}
