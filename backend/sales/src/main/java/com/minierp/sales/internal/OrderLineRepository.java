package com.minierp.sales.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface OrderLineRepository extends JpaRepository<OrderLine, UUID> {
    List<OrderLine> findByOrderIdOrderByLineNumberAsc(UUID orderId);
}
