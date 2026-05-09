package com.minierp.inventory.api;

import java.util.UUID;

public record WarehouseDto(
        UUID id,
        String code,
        String name,
        String address,
        String phone,
        boolean defaultWarehouse,
        boolean active) {}
