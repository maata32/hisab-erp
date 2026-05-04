package com.minierp.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCode(String code);
    List<Permission> findAllByCodeIn(List<String> codes);
}
