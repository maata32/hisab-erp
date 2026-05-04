package com.minierp.shared.security;

import java.util.Set;
import java.util.UUID;

public record CurrentUser(
        UUID userId,
        UUID tenantId,
        String email,
        String preferredLanguage,
        Set<String> roles,
        Set<String> permissions
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
