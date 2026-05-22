package com.minierp.customer.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SupplierBalanceRepository extends JpaRepository<SupplierBalance, UUID> {
    Optional<SupplierBalance> findBySupplierId(UUID supplierId);

    List<SupplierBalance> findBySupplierIdIn(Collection<UUID> supplierIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM SupplierBalance b WHERE b.supplierId = :supplierId")
    Optional<SupplierBalance> lockBySupplierId(UUID supplierId);
}
