package com.hisaberp.partner.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface PartnerRepository extends JpaRepository<Partner, UUID> {

    boolean existsByCode(String code);

    Optional<Partner> findByCode(String code);

    @Query("SELECT p FROM Partner p WHERE p.active = true AND " +
           "(:role IS NULL OR " +
           "(:role = 'CUSTOMER' AND p.isCustomer = true) OR " +
           "(:role = 'SUPPLIER' AND p.isSupplier = true))")
    Page<Partner> findActiveByRole(String role, Pageable pageable);

    @Query("SELECT p FROM Partner p WHERE p.active = true AND " +
           "(:role IS NULL OR " +
           "(:role = 'CUSTOMER' AND p.isCustomer = true) OR " +
           "(:role = 'SUPPLIER' AND p.isSupplier = true)) AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(p.code) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(COALESCE(p.phone,'')) LIKE LOWER(CONCAT('%',:q,'%')))")
    Page<Partner> searchByRole(String role, String q, Pageable pageable);

    @Query("SELECT MAX(p.code) FROM Partner p WHERE p.code LIKE :prefix")
    Optional<String> findMaxCodeByPrefix(String prefix);
}
