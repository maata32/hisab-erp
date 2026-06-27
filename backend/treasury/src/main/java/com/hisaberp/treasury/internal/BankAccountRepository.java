package com.hisaberp.treasury.internal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    List<BankAccount> findByActiveTrueOrderByNameAsc();

    List<BankAccount> findAllByOrderByActiveDescNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BankAccount b WHERE b.id = :id")
    Optional<BankAccount> lockById(@Param("id") UUID id);
}
