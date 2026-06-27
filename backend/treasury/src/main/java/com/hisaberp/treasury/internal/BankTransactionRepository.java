package com.hisaberp.treasury.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {

    Page<BankTransaction> findByBankAccountIdOrderByOccurredAtDesc(UUID bankAccountId, Pageable pageable);

    Page<BankTransaction> findByBankAccountIdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID bankAccountId, Instant from, Instant to, Pageable pageable);
}
