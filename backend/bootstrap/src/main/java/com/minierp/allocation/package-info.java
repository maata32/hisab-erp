/**
 * AllocationEngine — unified party-level matching between positive open items
 * (payments received, customer credits, supplier payments) and negative ones
 * (customer invoices, supplier invoices, refund payments). Phase 5 introduces
 * the double-write hook on payment confirmation, joining the publisher's
 * transaction via {@link PaymentAllocationListener}.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Allocation Engine",
        allowedDependencies = {
                "shared",
                "partner::customer-api",
                "sales::sales-api",
                "payment::payment-api"
        })
package com.minierp.allocation;

