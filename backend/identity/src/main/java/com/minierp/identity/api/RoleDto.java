package com.minierp.identity.api;

import java.util.UUID;

public record RoleDto(UUID id, String code, String name, String description) {}
