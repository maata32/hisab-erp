package com.minierp.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndTenantId(String email, UUID tenantId);
    boolean existsByEmailAndTenantId(String email, UUID tenantId);
}
