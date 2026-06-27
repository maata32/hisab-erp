package com.hisaberp.expense.internal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Page<Expense> findByCategoryId(UUID categoryId, Pageable pageable);

    @Query("SELECT e FROM Expense e WHERE e.nextRecurrenceDate <= :today AND e.recurring = true")
    List<Expense> findDueRecurrences(@Param("today") LocalDate today);

    /** Sum of committed (non-rejected) expense amounts for a category within a
     *  date window — used by the per-category cap approval check. */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM Expense e " +
           "WHERE e.categoryId = :categoryId AND e.approvalStatus <> :excluded " +
           "AND e.expenseDate BETWEEN :start AND :end")
    BigDecimal sumAmountByCategoryInPeriod(@Param("categoryId") UUID categoryId,
                                           @Param("start") LocalDate start,
                                           @Param("end") LocalDate end,
                                           @Param("excluded") ApprovalStatus excluded);
}
