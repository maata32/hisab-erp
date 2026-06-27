package com.hisaberp.uom.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Admin-list projection of a unit. Same shape as {@link UomDto} plus {@code inUse},
 * which tells the UI whether the unit is referenced anywhere (and therefore whether
 * its code / ratio / base may still be edited). Kept separate from {@link UomDto} so
 * the cross-module read contract stays stable.
 */
public record UomView(
        UUID id,
        UUID categoryId,
        String categoryCode,
        String code,
        String name,
        BigDecimal ratioToBase,
        boolean isBase,
        int decimalPlaces,
        boolean inUse
) {}
