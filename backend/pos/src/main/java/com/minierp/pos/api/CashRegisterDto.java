package com.minierp.pos.api;

import java.util.UUID;

public record CashRegisterDto(
        UUID id,
        String code,
        String name,
        UUID warehouseId,
        UUID defaultPriceTierId,
        int receiptWidthMm,
        boolean active) {}
