package com.minierp.customer.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    boolean existsByCode(String code);
    Optional<Supplier> findByCode(String code);
    Page<Supplier> findByActiveTrue(Pageable pageable);

    @Query("SELECT s FROM Supplier s WHERE s.active = true AND " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(s.code) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(s.phone) LIKE LOWER(CONCAT('%',:q,'%')))")
    Page<Supplier> search(String q, Pageable pageable);

    @Query("SELECT MAX(s.code) FROM Supplier s WHERE s.code LIKE :prefix")
    Optional<String> findMaxCodeByPrefix(String prefix);
}
