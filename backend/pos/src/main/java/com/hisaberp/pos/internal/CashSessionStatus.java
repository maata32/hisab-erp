package com.hisaberp.pos.internal;

enum CashSessionStatus {
    /** Cashier is selling, only one OPEN session allowed per register. */
    OPEN,
    /** Cashier has counted and submitted. Waiting for vault manager validation. */
    CLOSED,
    /** Vault manager confirmed receipt — countedClosing has been added to the vault. */
    VALIDATED
}
