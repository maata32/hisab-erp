package com.minierp.treasury.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inter-module API. Other modules (POS today, payment/expense later) call these
 * methods to mutate the central vault as part of their own business transactions.
 * Implementations are transactional — callers should expect the same transaction
 * scope (REQUIRES_NEW is NOT used so the caller can roll the entire flow back).
 */
public interface TreasuryOperations {

    /**
     * Vault is credited with the cash physically received from a closed POS session,
     * once a vault manager validates the cashier's deposit (see PosService#validateSession).
     */
    void depositFromPosSession(UUID sessionId, BigDecimal amount, UUID userId);

    /**
     * Move the central vault by a signed amount as part of a cash payment confirm
     * (positive = cash-in, negative = cash-out). No-op for a zero/null amount.
     * Joins the caller's transaction so it rolls back with the payment.
     */
    void recordVaultMovement(BigDecimal amountSigned, String referenceType, UUID referenceId,
                             UUID userId, String note);

    /**
     * Move a bank account by a signed amount as part of a non-cash payment confirm
     * (positive = cash-in, negative = cash-out). No-op for a zero/null amount.
     */
    void recordBankMovement(UUID bankAccountId, BigDecimal amountSigned, String referenceType,
                            UUID referenceId, UUID userId, String note);
}
