package com.hisaberp.sales.api;

public interface NumberingOperations {
    String nextDeliveryNumber();
    String nextReturnDeliveryNumber();
    String nextPaymentReceiptNumber();
    String nextPurchaseOrderNumber();
    String nextPurchaseInvoiceNumber();
    String nextGoodsReceiptNumber();
    String nextPurchaseCreditNoteNumber();
}
