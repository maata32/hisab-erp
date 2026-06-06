package com.minierp.treasury.internal;

enum BankTransactionType {
    /** Deposit from the central vault to this bank account. */
    DEPOSIT_FROM_VAULT,
    /** Withdrawal to the central vault from this bank account. */
    WITHDRAWAL_TO_VAULT,
    /** Manual reconciliation after a bank statement (signed amount). */
    ADJUSTMENT,
    /** A confirmed non-cash payment: positive for a cash-in, negative for a cash-out. */
    PAYMENT
}
