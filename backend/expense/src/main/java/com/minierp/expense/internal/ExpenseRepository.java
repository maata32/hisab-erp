package com.minierp.expense.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Page<Expense> findByCategoryId(UUID categoryId, Pageable pageable);

    @Query("SELECT e FROM Expense e WHERE e.nextRecurrenceDate <= :today AND e.recurring = true")
    List<Expense> findDueRecurrences(@Param("today") LocalDate today);
}
