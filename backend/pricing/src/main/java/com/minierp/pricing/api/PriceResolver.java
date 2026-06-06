package com.minierp.pricing.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Resolves the unit price for a variant/UoM/tier on a given date,
 * computes the line subtotal/tax/total, and applies an optional unit-discount.
 *
 * Resolution order (variant first, then product-level fallback for uniform pricing
 * and freshly generated variants that have no own rows yet):
 *  1) (variantId, uomId, tierId) row valid on `date` with smallest min_qty satisfied
 *  2) (variantId, uomId, defaultTier)
 *  3) (productId, uomId, tierId)  — fallback
 *  4) (productId, uomId, defaultTier) — fallback
 *  5) Throw a BusinessException with code "error.pricing.no_price"
 */
public interface PriceResolver {

    ResolvedPrice resolve(UUID variantId,
                          UUID uomId,
                          UUID priceTierId,
                          BigDecimal quantity,
                          LocalDate date,
                          BigDecimal unitDiscount);
}
