package com.hisaberp.expense.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface IncomeCategoryRepository extends JpaRepository<IncomeCategory, UUID> {
    List<IncomeCategory> findByActiveTrue();
}
