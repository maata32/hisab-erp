package com.hisaberp.audit.api;

/**
 * CDC §5.3 — canonical action codes for sensitive operations recorded in {@code audit_log}.
 *
 * <p>The {@code action} column is intentionally kept as {@code varchar(100)} for forward-compat
 * (modules may register custom codes), but call sites should prefer these constants for the
 * operations the CDC enumerates explicitly.</p>
 */
public final class AuditAction {

    private AuditAction() {}

    // CRUD
    public static final String CREATE  = "CREATE";
    public static final String UPDATE  = "UPDATE";
    public static final String DELETE  = "DELETE";

    // Document state transitions
    public static final String CANCEL              = "CANCEL";
    public static final String REFUND              = "REFUND";
    public static final String EXPORT              = "EXPORT";

    // Payments / credits
    public static final String PAYMENT_CREATED     = "PAYMENT_CREATED";
    public static final String PAYMENT_CANCELLED   = "PAYMENT_CANCELLED";
    public static final String CREDIT_GRANTED      = "CREDIT_GRANTED";
    public static final String CREDIT_WITHDRAWN    = "CREDIT_WITHDRAWN";

    // Lot / stock
    public static final String LOT_DESTROYED       = "LOT_DESTROYED";
    public static final String LOT_BLOCKED         = "LOT_BLOCKED";
    public static final String STOCK_ADJUSTED      = "STOCK_ADJUSTED";

    // Pricing / catalog
    public static final String PRICE_CHANGED       = "PRICE_CHANGED";
}
