package com.minierp.uom.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Public read-only contract over UoM data, exposed to other modules so they don't need
 * to depend on internal repositories.
 */
public interface UomLookup {

    Optional<UomDto> findById(UUID id);

    Optional<UomDto> findByCode(String code);

    /**
     * Convert {@code amount} expressed in {@code fromUomId} into the equivalent quantity
     * in {@code toUomId}. Throws if the two units are in different categories.
     * The result is rounded to the {@code decimalPlaces} of the target unit using HALF_UP.
     */
    BigDecimal convert(BigDecimal amount, UUID fromUomId, UUID toUomId);

    /**
     * Default rounding helper for any UoM-aware computation.
     */
    static BigDecimal round(BigDecimal value, int scale) {
        return value.setScale(scale, RoundingMode.HALF_UP);
    }
}
