package com.minierp.customer.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface CustomerRepository extends JpaRepository<Customer, UUID> {
    boolean existsByCode(String code);
    Optional<Customer> findByCode(String code);
    Page<Customer> findByActiveTrue(Pageable pageable);

    @Query("SELECT c FROM Customer c WHERE c.active = true AND " +
           "(LOWER(c.name) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(c.code) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%',:q,'%')))")
    Page<Customer> search(String q, Pageable pageable);
}
