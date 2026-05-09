package com.minierp.customer.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of customers, exposed to sales, delivery, and payment modules.
 */
public interface CustomerLookup {
    Optional<CustomerSummary> findById(UUID id);
    Optional<CustomerSummary> findByCode(String code);
}
