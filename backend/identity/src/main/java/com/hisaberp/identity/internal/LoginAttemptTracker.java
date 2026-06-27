package com.hisaberp.identity.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Persists failed-login bookkeeping (attempt counter + lock) in its OWN transaction.
 *
 * <p>{@code AuthService.login()} always throws (and therefore rolls back) on a bad password.
 * If the increment ran in that same transaction it would be discarded by the rollback and the
 * account could never lock (BUG-1 / AUTH-04). Recording it here under
 * {@code REQUIRES_NEW} commits the counter independently of the failing outer transaction.</p>
 *
 * <p>This MUST be a separate bean: {@code REQUIRES_NEW} only takes effect through the Spring
 * proxy, i.e. on a cross-bean call — a self-invocation would silently keep the outer transaction.</p>
 */
@Component
@RequiredArgsConstructor
public class LoginAttemptTracker {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final int LOCK_MINUTES = 15;

    private final UserRepository users;

    /** Increment the failed-attempt counter and lock the account once the threshold is reached. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedLogin(UUID userId) {
        users.findById(userId).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCK_MINUTES, ChronoUnit.MINUTES));
                user.setFailedLoginAttempts(0);
            } else {
                user.setFailedLoginAttempts(attempts);
            }
        });
    }
}
