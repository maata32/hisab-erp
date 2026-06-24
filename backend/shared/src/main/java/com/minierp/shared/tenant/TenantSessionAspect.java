package com.minierp.shared.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Activates the per-session tenant filter and sets the Postgres session variable
 * {@code app.current_tenant} (consumed by the RLS policies) inside every
 * transactional service call.
 *
 * <p>The aspect runs INSIDE the transaction by ordering itself higher than
 * Spring's transaction interceptor (alphabetical tiebreak — see
 * {@link Ordered#LOWEST_PRECEDENCE}). It only acts when a transaction is
 * already active; otherwise it's a no-op (e.g., during liquibase or startup).</p>
 *
 * <p>SUPER_ADMIN flows that legitimately span tenants must call
 * {@link TenantContext#set(UUID)} explicitly per target tenant.</p>
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class TenantSessionAspect {

    @PersistenceContext
    private EntityManager entityManager;

    @Around("@within(org.springframework.transaction.annotation.Transactional) " +
            "|| @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object aroundTransactional(ProceedingJoinPoint pjp) throws Throwable {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            UUID tenantId = TenantContext.tryGet().orElse(null);
            try {
                if (tenantId != null) {
                    entityManager.unwrap(Session.class)
                            .enableFilter("tenantFilter").setParameter("tenantId", tenantId);
                }
                // Pin the Postgres session var for THIS transaction (is_local=true) on EVERY
                // transactional call — to the tenant when present, otherwise empty. Setting it
                // UNCONDITIONALLY prevents a stale value left on a pooled connection (e.g. a
                // session-level set_config, or another tenant's prior transaction) from leaking
                // into a context-less transaction such as login. Under the non-superuser runtime
                // role that leak would make RLS hide the very rows the transaction needs (it broke
                // login for any tenant != the leaked one). Empty maps to NULL → RLS bypass, the
                // same as an unset context.
                entityManager.createNativeQuery("SELECT set_config('app.current_tenant', :v, true)")
                        .setParameter("v", tenantId == null ? "" : tenantId.toString())
                        .getSingleResult();
            } catch (RuntimeException ex) {
                log.warn("Could not activate tenant context on session: {}", ex.getMessage());
            }
        }
        return pjp.proceed();
    }
}
