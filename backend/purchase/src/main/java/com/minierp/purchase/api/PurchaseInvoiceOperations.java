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
}
