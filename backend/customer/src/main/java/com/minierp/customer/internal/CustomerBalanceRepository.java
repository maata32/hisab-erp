package com.minierp.customer.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

interface CustomerBalanceRepository extends JpaRepository<CustomerBalance, UUID> {
    Optional<CustomerBalance> findByCustomerId(UUID customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM CustomerBalance b WHERE b.customerId = :customerId")
    Optional<CustomerBalance> lockByCustomerId(UUID customerId);
}
