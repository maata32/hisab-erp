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
            if (tenantId != null) {
                try {
                    Session session = entityManager.unwrap(Session.class);
                    session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
                    entityManager.createNativeQuery("SELECT set_config('app.current_tenant', :v, true)")
                            .setParameter("v", tenantId.toString())
                            .getSingleResult();
                } catch (RuntimeException ex) {
                    log.warn("Could not activate tenant context on session: {}", ex.getMessage());
                }
            }
        }
        return pjp.proceed();
    }
}
