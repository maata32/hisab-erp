package com.minierp.sales.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Order> findAll(Pageable pageable);
}
