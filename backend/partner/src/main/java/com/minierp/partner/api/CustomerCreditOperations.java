package com.minierp.partner.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Programmatic creation of customer credits, used by the sales module when a
 * credit note exceeds the unpaid balance of the invoice it is issued against.
 */
public interface CustomerCreditOperations {

    /**
     * Grant an active credit to the customer.
     *
     * @param customerId the partner (customer) id
     * @param amount     positive credit amount
     * @param source     one of {@code DEPOSIT, OVERPAYMENT, REFUND, MANUAL_ADJUSTMENT}
     * @param notes      free-form note (origin number, etc.)
     * @return id of the new credit row
     */
    UUID grantCredit(UUID customerId, BigDecimal amount, String source, String notes);
}
