package com.minierp.purchase.internal;

/**
 * Auto-derived reception state of a purchase invoice — mirror of the sales
 * {@code InvoiceDeliveryStatus}. Recomputed from the quantities actually
 * received across non-cancelled inbound goods-receipts (BRC) anchored to the
 * invoice. {@code RETURNED} is set when a purchase credit note (avoir) sends
 * received goods back to the supplier.
 */
public enum PurchaseInvoiceReceptionStatus { NONE, PARTIALLY_RECEIVED, RECEIVED, RETURNED }
