package com.hisaberp.expense.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Expense facade used by other modules. Today only the payment module calls in:
 * when a CASH_OUT payment allocated to an {@code EXPENSE} target is confirmed,
 * {@link #applyPayment} settles the expense (raises paid_amount, lowers balance,
 * flips status to PAID once fully covered). Expense payments carry no partial
 * state — the only transition is UNPAID → PAID.
 */
public interface ExpenseOperations {

    /** Apply {@code amount} of cash against the expense's outstanding balance. */
    void applyPayment(UUID expenseId, BigDecimal amount);
}
