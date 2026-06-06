package com.minierp.catalog.api;

import java.util.UUID;

/**
 * Cross-module read projection of a product variant (the real stock-keeping unit).
 * Carries the parent {@code productId} and {@code baseUomId} so callers (inventory,
 * sales, purchase, pos, lots) can denormalize and validate without depending on
 * catalog internals. {@code displayLabel} is "Product — Rouge / M" for snapshots/UI.
 */
public record VariantView(
        UUID id,
        UUID productId,
        String sku,
        String barcode,
        UUID baseUomId,
        String productName,
        String attributesDisplay,
        String displayLabel,
        boolean defaultVariant,
        boolean active
) {}
