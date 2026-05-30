package com.minierp.sales.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Invoice facade used by other modules:
 *  - payment module: applyPayment / markOverdue / lookups
 *  - delivery module: shipment prerequisites + auto-derived delivery status
 */
public interface InvoiceOperations {
    Optional<InvoiceSummary> findById(UUID invoiceId);
    List<InvoiceSummary> findUnpaidByCustomer(UUID customerId);
    void applyPayment(UUID invoiceId, BigDecimal amount);
    /**
     * Reverse a previously applied payment of {@code amount} on the invoice —
     * used by the refund flow. Decreases paid_amount, raises balance, and walks
     * the status back to ISSUED/PARTIAL as appropriate. Clamps so paid_amount
     * never goes negative even if callers somehow over-reverse.
     */
    void reversePayment(UUID invoiceId, BigDecimal amount);
    /**
     * Settle part of the invoice via a credit note. Returns the amount that was
     * actually imputed (clamped to the current outstanding balance); the caller
     * is responsible for routing any surplus to a customer credit.
     */
    BigDecimal applyCredit(UUID invoiceId, BigDecimal amount);
    void markOverdue(UUID invoiceId);

    /**
     * Lines (productId → ordered quantity) on the invoice — used by delivery
     * module to detect partial-vs-full coverage when recomputing delivery status.
     */
    Map<UUID, java.math.BigDecimal> getInvoicedQtyByProduct(UUID invoiceId);

    /**
     * Re-derive the invoice's deliveryStatus from total quantities already
     * shipped per product (across non-cancelled BLs).
     */
    void recomputeDeliveryStatus(UUID invoiceId, Map<UUID, java.math.BigDecimal> totalDeliveredByProduct);

    /**
     * True when the invoice is in a state that allows a new BL: status not
     * DRAFT / CANCELLED and deliveryStatus not DELIVERED.
     */
    boolean canReceiveDelivery(UUID invoiceId);
}
