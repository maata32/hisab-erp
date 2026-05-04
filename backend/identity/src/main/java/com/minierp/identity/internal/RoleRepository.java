package com.minierp.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCodeAndTenantId(String code, UUID tenantId);
    List<Role> findAllByTenantId(UUID tenantId);
}
