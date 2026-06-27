package com.hisaberp.catalog.api;

import java.util.UUID;

public record BrandDto(
        UUID id,
        String code,
        String name,
        String description,
        String logoUrl,
        boolean active) {}
