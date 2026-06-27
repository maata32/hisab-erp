package com.hisaberp.catalog.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read-only contract over product variants, exposed to other modules so they
 * never touch catalog internals. Every product has at least one variant (a default
 * one for attribute-less products), so callers can always resolve a variant to its
 * parent product and base UoM.
 */
public interface VariantLookup {

    Optional<VariantView> findById(UUID variantId);

    /** All active variants of a product, ordered by SKU. */
    List<VariantView> listByProduct(UUID productId);

    /** The default (implicit) variant of an attribute-less product, if any. */
    Optional<VariantView> findDefaultForProduct(UUID productId);

    /**
     * Resolve a variant, throwing {@code entity.product_variant} when missing.
     * Convenience for callers that require the variant to exist.
     */
    VariantView require(UUID variantId);
}
