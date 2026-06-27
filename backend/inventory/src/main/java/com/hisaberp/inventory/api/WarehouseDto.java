package com.hisaberp.inventory.api;

import java.util.UUID;

public record WarehouseDto(
        UUID id,
        String code,
        String name,
        String address,
        String phone,
        String type,
        boolean defaultWarehouse,
        boolean active) {}
