package com.minierp.salesorchestration;

import com.minierp.delivery.api.DeliveryWriteOperations;
import com.minierp.sales.api.InvoiceWriteOperations;
import com.minierp.sales.api.SalesDto;
import com.minierp.shared.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * "Create invoice + ship it now" use case. If the immediate shipment cannot
 * be honoured for any reason (insufficient stock, missing warehouse, etc.),
 * the whole transaction rolls back: no invoice, no delivery, no balance
 * touched. The user gets the same outcome as if they had never clicked save.
 */
@Service
@RequiredArgsConstructor
public class InvoiceWithDeliveryService {

    private final InvoiceWriteOperations invoiceOps;
    private final DeliveryWriteOperations deliveryOps;

    @Transactional
    public SalesDto.InvoiceDto createAndShip(SalesDto.CreateInvoiceRequest req, UUID warehouseId) {
        UUID userId = CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
        SalesDto.InvoiceDto invoice = invoiceOps.createInvoice(req);
        deliveryOps.shipInvoiceImmediately(invoice.id(), warehouseId, userId);
        // Re-fetch so the returned DTO reflects deliveryStatus = DELIVERED
        // that recordDelivery just persisted.
        return invoiceOps.getInvoice(invoice.id());
    }
}
