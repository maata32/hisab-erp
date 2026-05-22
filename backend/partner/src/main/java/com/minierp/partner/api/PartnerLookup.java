package com.minierp.partner.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of partners, exposed cross-module. Returns the same
 * {@link PartnerSummary} regardless of role — callers should filter on
 * {@code isCustomer()} / {@code isSupplier()} before treating a party as
 * one or the other.
 */
public interface PartnerLookup {
    Optional<PartnerSummary> findById(UUID id);
    Optional<PartnerSummary> findByCustomerCode(String customerCode);
    Optional<PartnerSummary> findBySupplierCode(String supplierCode);
}
