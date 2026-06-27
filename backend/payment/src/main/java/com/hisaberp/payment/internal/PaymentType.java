package com.hisaberp.payment.internal;

/**
 * Cash direction — the only axis that matters in the unified net-position
 * ledger. The customer/supplier nature and the refund-or-not nuance are not
 * encoded here: a cash-in is a cash-in and a cash-out is a cash-out, whatever
 * the party. Migrated from the former party-based enum (changelog 0062/0065):
 *   CASH_IN  from CUSTOMER_PAYMENT, CUSTOMER_DEPOSIT, SUPPLIER_REFUND (cash received)
 *   CASH_OUT from SUPPLIER_PAYMENT, CUSTOMER_CREDIT_WITHDRAWAL, CUSTOMER_REFUND (cash paid out)
 */
public enum PaymentType {
    CASH_IN, CASH_OUT
}
