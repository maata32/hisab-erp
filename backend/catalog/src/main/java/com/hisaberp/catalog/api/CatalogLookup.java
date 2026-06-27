package com.hisaberp.catalog.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view over the catalog, exposed to other modules (pricing, inventory, pos).
 * Implementations are tenant-scoped via the standard TenantContext + Hibernate filter.
 */
public interface CatalogLookup {

    Optional<ProductSnapshot> findProductById(UUID id);

    Optional<ProductSnapshot> findProductBySku(String sku);

    Optional<ProductSnapshot> findProductByBarcode(String barcode);

    List<ProductSnapshot> findProductsByIds(List<UUID> ids);
}
