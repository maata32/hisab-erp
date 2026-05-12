package com.minierp.payment.internal;

/**
 * Polymorphic payment target. CDC §3.6.2.
 *
 * PURCHASE_INVOICE and SALARY are reserved for Phase 2 (purchase module) and
 * Phase 3 (hr module) respectively — accepted as allocation targets, but the
 * cross-module update listeners will only become functional once those modules ship.
 */
public enum AllocationTargetType {
    SALE_INVOICE,
    PURCHASE_INVOICE,
    CUSTOMER_BALANCE,
    SALE,
    CUSTOMER_CREDIT,
    EXPENSE,
    SALARY
}
