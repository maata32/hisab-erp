package com.minierp.customer.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of suppliers, exposed to purchase, expense and lot-expiry modules.
 */
public interface SupplierLookup {
    Optional<SupplierSummary> findById(UUID id);
    Optional<SupplierSummary> findByCode(String code);
}
