package com.minierp.pos.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CashRegisterRepository extends JpaRepository<CashRegister, UUID> {
    Optional<CashRegister> findByCode(String code);
    boolean existsByCode(String code);
    List<CashRegister> findByActiveTrue();
}
