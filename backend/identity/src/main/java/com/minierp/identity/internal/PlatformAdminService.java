package com.minierp.identity.internal;

import com.minierp.identity.api.PlatformDto;
import com.minierp.identity.api.UserDto;
import com.minierp.identity.security.PasswordHasher;
import com.minierp.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-tenant operations for the platform (super-admin) console. Callers reach these
 * via a platform token (tid=null) so there is NO tenant context — RLS is bypassed and
 * queries/updates span every tenant. All entry points are gated to SUPER_ADMIN at the
 * controller. Mirrors the user lifecycle of {@link UserService} but without the
 * current-tenant scoping (the organization is addressed explicitly).
 */
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String PWD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private final UserRepository users;
    private final PasswordHasher hasher;

    @Transactional(readOnly = true)
    public List<UserDto> listUsers(UUID organizationId) {
        return users.findAllByTenantIdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlatformDto.OrgUserCount> userCounts() {
        return users.countUsersByTenant().stream()
                .map(c -> new PlatformDto.OrgUserCount(c.getTenantId(), c.getCount()))
                .toList();
    }

    /** Returns a freshly generated temporary password. The caller must transmit it securely. */
    @Transactional
    public String resetPassword(UUID userId) {
        User u = load(userId);
        String temp = randomPassword(12);
        u.setPasswordHash(hasher.hash(temp));
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        u.setPasswordChangedAt(Instant.now());
        return temp;
    }

    @Transactional
    public void unlock(UUID userId) {
        User u = load(userId);
        u.setLockedUntil(null);
        u.setFailedLoginAttempts(0);
    }

    @Transactional
    public UserDto setActive(UUID userId, boolean active) {
        User u = load(userId);
        u.setActive(active);
        return toDto(u);
    }

    private User load(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new NotFoundException("user.not_found", Map.of("id", userId)));
    }

    private UserDto toDto(User u) {
        return new UserDto(
                u.getId(),
                u.getEmail(),
                u.getFullName(),
                u.getPhone(),
                u.getPreferredLanguage(),
                u.isActive(),
                u.getLastLoginAt(),
                u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now()),
                u.getRoles().stream().map(Role::getCode).sorted().toList());
    }

    private static String randomPassword(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(PWD_CHARS.charAt(RNG.nextInt(PWD_CHARS.length())));
        }
        return sb.toString();
    }
}
