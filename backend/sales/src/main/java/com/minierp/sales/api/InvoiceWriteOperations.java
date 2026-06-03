package com.minierp.sales.api;

import java.util.UUID;

/**
 * Write-side facade for invoices, used by the sales-orchestration sub-module
 * (which lives in bootstrap and combines invoice creation with downstream
 * delivery operations). The HTTP controller in this module delegates here too,
 * but most callers go through that controller; this interface exists purely
 * so cross-module orchestrators can call the same business logic without
 * reaching into the internal package.
 */
public interface InvoiceWriteOperations {

    SalesDto.InvoiceDto createInvoice(SalesDto.CreateInvoiceRequest req);

    SalesDto.InvoiceDto getInvoice(UUID id);
}
