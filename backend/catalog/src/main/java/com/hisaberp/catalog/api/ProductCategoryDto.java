package com.hisaberp.catalog.api;

import java.util.UUID;

public record ProductCategoryDto(
        UUID id,
        String code,
        String name,
        UUID parentId,
        int sortOrder,
        boolean active) {}
