package com.minierp.tenant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record OrganizationTypeDto(
        UUID id,
        String code,
        String label,
        int sortOrder,
        boolean active
) {

    public record CreateRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 100) String label,
            Integer sortOrder
    ) {}

    public record UpdateRequest(
            @Size(max = 100) String label,
            Integer sortOrder,
            Boolean active
    ) {}
}
