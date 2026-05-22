package com.minierp.partner.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface PartnerRepository extends JpaRepository<Partner, UUID> {

    boolean existsByCustomerCode(String customerCode);
    boolean existsBySupplierCode(String supplierCode);

    Optional<Partner> findByCustomerCode(String customerCode);
    Optional<Partner> findBySupplierCode(String supplierCode);

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
           "LOWER(COALESCE(p.customerCode,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(COALESCE(p.supplierCode,'')) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(COALESCE(p.phone,'')) LIKE LOWER(CONCAT('%',:q,'%')))")
    Page<Partner> searchByRole(String role, String q, Pageable pageable);

    @Query("SELECT MAX(p.customerCode) FROM Partner p WHERE p.customerCode LIKE :prefix")
    Optional<String> findMaxCustomerCodeByPrefix(String prefix);

    @Query("SELECT MAX(p.supplierCode) FROM Partner p WHERE p.supplierCode LIKE :prefix")
    Optional<String> findMaxSupplierCodeByPrefix(String prefix);
}
