package com.minierp.purchase.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Purchase-invoice mutation facade consumed by the payment module so it can apply
 * supplier payments to specific invoices (PaymentAllocation.PURCHASE_INVOICE).
 */
public interface PurchaseInvoiceOperations {
    Optional<PurchaseInvoiceSummary> findById(UUID id);
    List<PurchaseInvoiceSummary> findUnpaidBySupplier(UUID supplierId);
    void applyPayment(UUID purchaseInvoiceId, BigDecimal amount);
    /**
     * Reverse a previously applied payment of {@code amount} on the invoice —
     * used by the supplier-refund flow. Decreases paid_amount, raises balance,
     * walks status back to ISSUED/PARTIAL as appropriate, never lets paid go
     * negative.
     */
    void reversePayment(UUID purchaseInvoiceId, BigDecimal amount);
}
