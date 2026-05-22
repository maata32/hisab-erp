package com.minierp.sales.api;

public interface NumberingOperations {
    String nextDeliveryNumber();
    String nextPaymentReceiptNumber();
    String nextPurchaseOrderNumber();
    String nextPurchaseInvoiceNumber();
}
