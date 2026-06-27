package com.hisaberp.treasury.internal;

enum VaultMovementType {
    /** Cash leaves vault to fund a POS session opening float. */
    TO_POS_SESSION,
    /** Cash returns to vault when a POS session is closed (counted cash). */
    FROM_POS_SESSION,
    /** Cash leaves vault for a bank deposit. */
    TO_BANK,
    /** Cash returns to vault from a bank withdrawal. */
    FROM_BANK,
    /** Manual reconciliation after a physical inventory of the safe. Signed amount. */
    ADJUSTMENT,
    /** A confirmed cash payment: positive for a cash-in, negative for a cash-out. */
    PAYMENT
}
