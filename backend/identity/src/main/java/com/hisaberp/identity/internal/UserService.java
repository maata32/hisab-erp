package com.hisaberp.identity.internal;

import com.hisaberp.identity.api.RoleDto;
import com.hisaberp.identity.api.UserDto;
import com.hisaberp.identity.security.PasswordHasher;
import com.hisaberp.shared.error.NotFoundException;
import com.hisaberp.shared.error.ValidationException;
import com.hisaberp.shared.security.CurrentUserHolder;
import com.hisaberp.shared.tenant.TenantContext;
import com.hisaberp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final SecureRandom RNG = new SecureRandom();
    private static final String PWD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordHasher hasher;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(String q, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            if (q == null || q.isBlank()) return cb.conjunction();
            String like = "%" + q.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("fullName")), like));
        };
        Page<User> page = users.findAll(spec, pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public UserDto get(UUID id) {
        return toDto(loadInTenant(id));
    }

    @Transactional(readOnly = true)
    public List<RoleDto> listRoles() {
        return roles.findAll().stream()
                .map(r -> new RoleDto(r.getId(), r.getCode(), r.getName(), r.getDescription()))
                .toList();
    }

    @Transactional
    public UserDto create(UserDto.CreateRequest req) {
        UUID tenantId = currentTenant();
        if (users.existsByEmailAndTenantId(req.email(), tenantId)) {
            throw new ValidationException("user.email_exists", Map.of("email", req.email()));
        }
        Set<Role> roleSet = resolveRoles(req.roleCodes());
        User u = User.builder()
                .email(req.email())
                .fullName(req.fullName())
                .phone(req.phone())
                .preferredLanguage(req.preferredLanguage() == null ? "fr" : req.preferredLanguage())
                .passwordHash(hasher.hash(req.password()))
                .active(true)
                .roles(roleSet)
                .build();
        u.setTenantId(tenantId);
        return toDto(users.save(u));
    }

    @Transactional
    public UserDto update(UUID id, UserDto.UpdateRequest req) {
        User u = loadInTenant(id);
        if (req.email() != null && !req.email().equals(u.getEmail())) {
            if (users.existsByEmailAndTenantId(req.email(), u.getTenantId())) {
                throw new ValidationException("user.email_exists", Map.of("email", req.email()));
            }
            u.setEmail(req.email());
        }
        if (req.fullName() != null) u.setFullName(req.fullName());
        if (req.phone() != null) u.setPhone(req.phone());
        if (req.preferredLanguage() != null) u.setPreferredLanguage(req.preferredLanguage());
        if (req.active() != null) u.setActive(req.active());
        if (req.roleCodes() != null) u.setRoles(resolveRoles(req.roleCodes()));
        return toDto(u);
    }

    @Transactional
    public void deactivate(UUID id) {
        User u = loadInTenant(id);
        // Prevent self-deactivation
        UUID actor = CurrentUserHolder.tryGet().map(c -> c.userId()).orElse(null);
        if (actor != null && actor.equals(u.getId())) {
            throw new ValidationException("user.self_deactivate", Map.of());
        }
        u.setActive(false);
    }

    /** Returns a freshly generated temporary password. The caller must transmit it securely. */
    @Transactional
    public String resetPassword(UUID id) {
        User u = loadInTenant(id);
        String temp = randomPassword(12);
        u.setPasswordHash(hasher.hash(temp));
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        u.setPasswordChangedAt(java.time.Instant.now());
        return temp;
    }

    @Transactional
    public void unlock(UUID id) {
        User u = loadInTenant(id);
        u.setLockedUntil(null);
        u.setFailedLoginAttempts(0);
    }

    private User loadInTenant(UUID id) {
        User u = users.findById(id)
                .orElseThrow(() -> new NotFoundException("user.not_found", Map.of("id", id)));
        UUID tenantId = currentTenant();
        if (!tenantId.equals(u.getTenantId())) {
            throw new NotFoundException("user.not_found", Map.of("id", id));
        }
        return u;
    }

    private UUID currentTenant() {
        return TenantContext.tryGet()
                .orElseThrow(() -> new ValidationException("tenant.context_missing", Map.of()));
    }

    private Set<Role> resolveRoles(List<String> codes) {
        if (codes == null || codes.isEmpty()) return new HashSet<>();
        Set<String> wanted = Set.copyOf(codes);
        Set<Role> found = roles.findAll().stream()
                .filter(r -> wanted.contains(r.getCode()))
                .collect(Collectors.toSet());
        Set<String> foundCodes = found.stream().map(Role::getCode).collect(Collectors.toSet());
        for (String code : wanted) {
            if (!foundCodes.contains(code)) {
                throw new ValidationException("role.not_found", Map.of("code", code));
            }
        }
        return found;
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
                u.getLockedUntil() != null && u.getLockedUntil().isAfter(java.time.Instant.now()),
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
