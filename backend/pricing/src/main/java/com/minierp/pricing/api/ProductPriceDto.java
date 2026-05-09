package com.minierp.pricing.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProductPriceDto(
        UUID id,
        UUID productId,
        UUID uomId,
        UUID priceTierId,
        BigDecimal amount,
        String currency,
        boolean taxInclusive,
        LocalDate validFrom,
        LocalDate validTo,
        BigDecimal minQty) {}
