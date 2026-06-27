package com.hisaberp.pricing.api;

import java.util.UUID;

public record PriceTierDto(
        UUID id,
        String code,
        String name,
        boolean defaultTier,
        boolean active) {}
