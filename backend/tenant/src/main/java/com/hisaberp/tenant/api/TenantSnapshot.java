package com.hisaberp.tenant.api;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.UUID;

public record TenantSnapshot(
        UUID id,
        String code,
        String name,
        String type,
        String status,
        String currency,
        String locale,
        String timezone
) {
    @JsonIgnore
    public boolean isOperational() {
        return "TRIAL".equals(status) || "ACTIVE".equals(status);
    }
}
