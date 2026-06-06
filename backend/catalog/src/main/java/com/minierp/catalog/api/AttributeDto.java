package com.minierp.catalog.api;

import java.util.List;
import java.util.UUID;

/** A variant axis (e.g. "Couleur") together with its possible values. */
public record AttributeDto(
        UUID id,
        String name,
        int sortOrder,
        boolean active,
        List<AttributeValueDto> values) {

    public record AttributeValueDto(
            UUID id,
            UUID attributeId,
            String value,
            String code,
            int sortOrder,
            boolean active) {}
}
