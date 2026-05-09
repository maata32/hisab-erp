package com.minierp.pricing.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Resolves the unit price for a product/UoM/tier on a given date,
 * computes the line subtotal/tax/total, and applies an optional unit-discount.
 *
 * Resolution order:
 *  1) Exact (productId, uomId, tierId) row valid on `date` with smallest min_qty satisfied
 *  2) Same combination at the default tier
 *  3) Throw a BusinessException with code "error.pricing.no_price"
 */
public interface PriceResolver {

    ResolvedPrice resolve(UUID productId,
                          UUID uomId,
                          UUID priceTierId,
                          BigDecimal quantity,
                          LocalDate date,
                          BigDecimal unitDiscount);
}
