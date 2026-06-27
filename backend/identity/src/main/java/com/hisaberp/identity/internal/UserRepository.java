package com.hisaberp.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);
    boolean existsByEmailAndTenantId(String email, UUID tenantId);

    /** Platform login: resolve a super-admin by email across all tenants (RLS bypassed). */
    Optional<User> findByEmailAndSuperAdminTrue(String email);

    /** Platform console: list a given organization's users (caller runs without tenant context). */
    List<User> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** Platform console: user count per organization (caller runs without tenant context). */
    @Query("select u.tenantId as tenantId, count(u) as count from User u group by u.tenantId")
    List<TenantUserCount> countUsersByTenant();

    interface TenantUserCount {
        UUID getTenantId();
        long getCount();
    }
}
