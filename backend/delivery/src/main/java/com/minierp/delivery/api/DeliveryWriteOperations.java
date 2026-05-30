package com.minierp.delivery.api;

import java.util.UUID;

/**
 * Write-side delivery facade for cross-module orchestrators. Today only one
 * use case is needed: "immediate shipment" of a freshly created invoice,
 * collapsing the create + start + record steps into a single transactional
 * call so the caller can run it inside its own larger transaction.
 */
public interface DeliveryWriteOperations {

    /**
     * Create an OUTBOUND BL covering every line of {@code invoiceId} at full
     * quantity, start it, and record it as DELIVERED right away — issuing the
     * corresponding stock movement. Throws {@code error.inventory.insufficient_stock}
     * if any line cannot be shipped from {@code warehouseId}.
     *
     * @return id of the created delivery
     */
    UUID shipInvoiceImmediately(UUID invoiceId, UUID warehouseId, UUID userId);
}
