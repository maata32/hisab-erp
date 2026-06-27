package com.hisaberp.sales.internal;

import com.hisaberp.sales.api.NumberingOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NumberingFacadeImpl implements NumberingOperations {

    private final NumberingService numbering;

    @Override
    public String nextDeliveryNumber() {
        return numbering.next(DocumentType.DELIVERY_NOTE);
    }

    @Override
    public String nextReturnDeliveryNumber() {
        return numbering.next(DocumentType.RETURN_DELIVERY);
    }

    @Override
    public String nextPaymentReceiptNumber() {
        return numbering.next(DocumentType.PAYMENT_RECEIPT);
    }

    @Override
    public String nextPurchaseOrderNumber() {
        return numbering.next(DocumentType.PURCHASE_ORDER);
    }

    @Override
    public String nextPurchaseInvoiceNumber() {
        return numbering.next(DocumentType.PURCHASE_INVOICE);
    }

    @Override
    public String nextGoodsReceiptNumber() {
        return numbering.next(DocumentType.GOODS_RECEIPT);
    }

    @Override
    public String nextPurchaseCreditNoteNumber() {
        return numbering.next(DocumentType.PURCHASE_CREDIT_NOTE);
    }
}
