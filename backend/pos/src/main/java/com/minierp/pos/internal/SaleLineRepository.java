package com.minierp.pos.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SaleLineRepository extends JpaRepository<SaleLine, UUID> {
    List<SaleLine> findBySaleIdOrderByLineNumberAsc(UUID saleId);
}
