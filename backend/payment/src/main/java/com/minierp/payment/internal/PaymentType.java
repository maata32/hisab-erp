package com.minierp.payment.internal;

/**
 * Party-agnostic cash-movement taxonomy. The direction (in/out) and whether the
 * movement is a refund are encoded here; the customer-vs-supplier nature is
 * derived from the party's role (single unified partner model). Migrated from
 * the former 6-value CUSTOMER/SUPPLIER enum (see changelog 0062):
 *   CASH_IN          from CUSTOMER_PAYMENT, CUSTOMER_DEPOSIT (cash received)
 *   CASH_OUT         from SUPPLIER_PAYMENT, CUSTOMER_CREDIT_WITHDRAWAL (cash paid out)
 *   CASH_IN_REFUND   from SUPPLIER_REFUND (a supplier refunds us - cash in)
 *   CASH_OUT_REFUND  from CUSTOMER_REFUND (we refund a customer - cash out)
 */
public enum PaymentType {
    CASH_IN, CASH_OUT, CASH_IN_REFUND, CASH_OUT_REFUND
}
