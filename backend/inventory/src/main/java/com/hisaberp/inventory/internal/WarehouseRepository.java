package com.hisaberp.inventory.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    Optional<Warehouse> findByCode(String code);
    boolean existsByCode(String code);
    Optional<Warehouse> findFirstByDefaultWarehouseTrue();
    List<Warehouse> findByActiveTrue();
}
