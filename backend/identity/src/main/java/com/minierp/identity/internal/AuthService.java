package com.minierp.identity.internal;

import com.minierp.identity.api.AuthController.LoginRequest;
import com.minierp.identity.api.AuthController.LoginResponse;
import com.minierp.identity.api.AuthController.PlatformLoginRequest;
import com.minierp.identity.events.UserLoggedInEvent;
import com.minierp.identity.security.JwtProperties;
import com.minierp.identity.security.JwtService;
import com.minierp.identity.security.PasswordHasher;
import com.minierp.shared.audit.AuditEvent;
import com.minierp.shared.audit.RequestContext;
import com.minierp.shared.error.ForbiddenException;
import com.minierp.shared.error.ValidationException;
import com.minierp.shared.security.CurrentUser;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.tenant.api.TenantLookup;
import com.minierp.tenant.api.TenantSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final JwtService jwtService;
    private final JwtProperties jwtProps;
    private final PasswordHasher hasher;
    private final TenantLookup tenantLookup;
    private final ApplicationEventPublisher events;
    private final LoginAttemptTracker loginAttempts;

    @Transactional
    public LoginResponse login(LoginRequest req) {
        TenantSnapshot tenant = resolveTenant(req.tenantCode());
        try {
            TenantContext.set(tenant.id());
            User user = users.findByEmailAndTenantId(req.email(), tenant.id())
                    .orElseThrow(() -> new BadCredentialsException("Unknown user"));

            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
                // Ordered map: the i18n template uses positional {0}/{1} and the exception
                // handler reads values() in order, so attempts must precede minutes
                // (Map.of gives no ordering guarantee — that swapped the message).
                LinkedHashMap<String, Object> lockArgs = new LinkedHashMap<>();
                lockArgs.put("attempts", LoginAttemptTracker.MAX_FAILED_ATTEMPTS);
                lockArgs.put("minutes", LoginAttemptTracker.LOCK_MINUTES);
                throw new ForbiddenException("auth.account_locked", lockArgs);
            }

            if (!hasher.verify(user.getPasswordHash(), req.password())) {
                // Record the failure in a SEPARATE transaction: this login() transaction always
                // rolls back when it throws below, which would otherwise discard the increment and
                // the account would never lock (BUG-1 / AUTH-04).
                loginAttempts.recordFailedLogin(user.getId());
                throw new BadCredentialsException("Invalid credentials");
            }

            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);

            // Block login for non-operational tenants. Super-admins bypass so they
            // can still sign in to recover/reactivate a suspended tenant.
            if (!user.isSuperAdmin()) {
                ensureTenantLoggable(tenant);
            }

            user.setLastLoginAt(Instant.now());

            CurrentUser currentUser = toCurrentUser(user);
            String accessToken = jwtService.issueAccessToken(currentUser);
            String refreshTokenPlain = issueRefreshToken(user, false);

            events.publishEvent(new UserLoggedInEvent(user.getId(), tenant.id(), Instant.now()));
            events.publishEvent(audit(user, "LOGIN_SUCCESS", null));

            return new LoginResponse(
                    accessToken,
                    refreshTokenPlain,
                    jwtProps.accessTokenTtl().toSeconds(),
                    currentUser);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Platform (super-admin) login — NOT scoped to a tenant. Runs without a tenant
     * context so RLS is bypassed and the super-admin is resolvable by email across
     * every tenant. The issued token carries {@code tid=null} and only the
     * {@code SUPER_ADMIN} role (no business permissions), confining it to platform
     * endpoints.
     */
    @Transactional
    public LoginResponse platformLogin(PlatformLoginRequest req) {
        User user = users.findByEmailAndSuperAdminTrue(req.email())
                .orElseThrow(() -> new BadCredentialsException("Unknown super admin"));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            LinkedHashMap<String, Object> lockArgs = new LinkedHashMap<>();
            lockArgs.put("attempts", LoginAttemptTracker.MAX_FAILED_ATTEMPTS);
            lockArgs.put("minutes", LoginAttemptTracker.LOCK_MINUTES);
            throw new ForbiddenException("auth.account_locked", lockArgs);
        }

        if (!hasher.verify(user.getPasswordHash(), req.password())) {
            loginAttempts.recordFailedLogin(user.getId());
            throw new BadCredentialsException("Invalid credentials");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());

        CurrentUser currentUser = toPlatformCurrentUser(user);
        String accessToken = jwtService.issueAccessToken(currentUser);
        String refreshTokenPlain = issueRefreshToken(user, true);

        events.publishEvent(new UserLoggedInEvent(user.getId(), user.getTenantId(), Instant.now()));
        events.publishEvent(audit(user, "PLATFORM_LOGIN_SUCCESS", null));

        return new LoginResponse(
                accessToken,
                refreshTokenPlain,
                jwtProps.accessTokenTtl().toSeconds(),
                currentUser);
    }

    @Transactional
    public LoginResponse refresh(String refreshTokenPlain) {
        String hash = hashToken(refreshTokenPlain);
        RefreshToken token = tokens.findByTokenHash(hash)
                .orElseThrow(() -> new ValidationException("auth.refresh_token_invalid"));
        if (!token.isActive()) {
            throw new ValidationException("auth.refresh_token_invalid");
        }
        boolean platform = token.isPlatform();
        try {
            // Platform sessions stay cross-tenant (no context → RLS bypass); tenant sessions
            // re-pin to their tenant. Either way the original refresh token was found above
            // before any context was set (login/refresh requests carry no tenant).
            if (!platform) {
                TenantContext.set(token.getTenantId());
            }
            User user = users.findById(token.getUserId())
                    .orElseThrow(() -> new ValidationException("auth.refresh_token_invalid"));

            // Rotate: revoke old, issue new (preserving the session kind)
            token.setRevokedAt(Instant.now());
            String newRefreshPlain = issueRefreshToken(user, platform);

            CurrentUser currentUser = platform ? toPlatformCurrentUser(user) : toCurrentUser(user);
            String accessToken = jwtService.issueAccessToken(currentUser);
            return new LoginResponse(
                    accessToken,
                    newRefreshPlain,
                    jwtProps.accessTokenTtl().toSeconds(),
                    currentUser);
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    public void logout(String refreshTokenPlain) {
        if (refreshTokenPlain == null || refreshTokenPlain.isBlank()) return;
        String hash = hashToken(refreshTokenPlain);
        tokens.findByTokenHash(hash).ifPresent(t -> t.setRevokedAt(Instant.now()));
    }

    private TenantSnapshot resolveTenant(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new ValidationException("tenant.not_found", Map.of("code", "<missing>"));
        }
        return tenantLookup.findByCode(tenantCode)
                .orElseThrow(() -> new com.minierp.shared.error.NotFoundException(
                        "tenant.not_found", Map.of("code", tenantCode)));
    }

    /** Reject sign-in for tenants that are not in a usable state. PAST_DUE stays usable (grace period). */
    private void ensureTenantLoggable(TenantSnapshot tenant) {
        switch (tenant.status()) {
            case "PENDING" -> throw new ForbiddenException("auth.tenant_pending");
            case "SUSPENDED" -> throw new ForbiddenException("auth.tenant_suspended");
            case "ARCHIVED" -> throw new ForbiddenException("auth.tenant_archived");
            default -> { /* TRIAL, ACTIVE, PAST_DUE: allowed */ }
        }
    }

    private CurrentUser toCurrentUser(User user) {
        Set<String> roleCodes = user.getRoles().stream()
                .map(Role::getCode).collect(Collectors.toCollection(java.util.HashSet::new));
        // Platform super-admins carry the SUPER_ADMIN role (it is a flag on the user,
        // not a tenant role) so @PreAuthorize("hasRole('SUPER_ADMIN')") endpoints work.
        if (user.isSuperAdmin()) {
            roleCodes.add("SUPER_ADMIN");
        }
        Set<String> permCodes = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        return new CurrentUser(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getPreferredLanguage(),
                roleCodes,
                permCodes);
    }

    /**
     * Platform session principal: no tenant binding (tid=null) and only the
     * {@code SUPER_ADMIN} authority — no business permissions — so the token can reach
     * exclusively {@code hasRole('SUPER_ADMIN')} endpoints, never tenant business data.
     */
    private CurrentUser toPlatformCurrentUser(User user) {
        return new CurrentUser(
                user.getId(),
                null,
                user.getEmail(),
                user.getPreferredLanguage(),
                Set.of("SUPER_ADMIN"),
                Set.of());
    }

    private String issueRefreshToken(User user, boolean platform) {
        byte[] raw = new byte[48];
        RNG.nextBytes(raw);
        String plain = HexFormat.of().formatHex(raw);
        RefreshToken rt = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(hashToken(plain))
                .expiresAt(Instant.now().plus(jwtProps.refreshTokenTtl()))
                .platform(platform)
                .build();
        rt.setTenantId(user.getTenantId());
        var hold = RequestContext.tryGet().orElse(null);
        if (hold != null) {
            rt.setIpAddress(hold.ipAddress());
            rt.setUserAgent(hold.userAgent());
        }
        tokens.save(rt);
        return plain;
    }

    private String hashToken(String plain) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private AuditEvent audit(User user, String action, Map<String, Object> details) {
        var ctx = RequestContext.tryGet().orElse(null);
        return AuditEvent.builder()
                .tenantId(user.getTenantId())
                .actorUserId(user.getId())
                .action(action)
                .entityType("User")
                .entityId(user.getId().toString())
                .ipAddress(ctx != null ? ctx.ipAddress() : null)
                .userAgent(ctx != null ? ctx.userAgent() : null)
                .newValue(details == null ? Map.of() : details)
                .build();
    }
}
