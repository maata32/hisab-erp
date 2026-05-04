package com.minierp.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByCode(String code);
    boolean existsByCode(String code);
}
