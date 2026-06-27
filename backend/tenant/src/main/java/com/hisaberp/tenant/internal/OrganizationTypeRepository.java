package com.hisaberp.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrganizationTypeRepository extends JpaRepository<OrganizationType, UUID> {
    Optional<OrganizationType> findByCode(String code);
    boolean existsByCode(String code);
    List<OrganizationType> findAllByOrderBySortOrderAscLabelAsc();
    List<OrganizationType> findAllByActiveTrueOrderBySortOrderAscLabelAsc();
}
