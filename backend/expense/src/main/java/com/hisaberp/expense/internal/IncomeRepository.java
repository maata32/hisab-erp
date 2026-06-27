package com.hisaberp.expense.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface IncomeRepository extends JpaRepository<Income, UUID> {
    Page<Income> findByCategoryId(UUID categoryId, Pageable pageable);
}
