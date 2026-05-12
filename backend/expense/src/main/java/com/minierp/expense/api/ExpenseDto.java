package com.minierp.expense.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ExpenseDto {

    private ExpenseDto() {}

    // ── Expense Category ──────────────────────────────────────────────────────

    public record CategoryResponse(UUID id, String name, UUID parentId, String color, boolean active) {}

    public record CreateCategoryRequest(
            @NotBlank String name,
            UUID parentId,
            String color) {}

    public record UpdateCategoryRequest(
            @NotBlank String name,
            UUID parentId,
            String color,
            Boolean active) {}

    // ── Income Category ───────────────────────────────────────────────────────

    public record IncomeCategoryResponse(UUID id, String name, UUID parentId, boolean active) {}

    public record CreateIncomeCategoryRequest(
            @NotBlank String name,
            UUID parentId) {}

    // ── Expense ───────────────────────────────────────────────────────────────

    public record ExpenseResponse(
            UUID id,
            String expenseNumber,
            UUID categoryId,
            UUID supplierId,
            BigDecimal amount,
            LocalDate expenseDate,
            String description,
            String paymentMethod,
            String paymentStatus,
            BigDecimal paidAmount,
            BigDecimal balance,
            List<String> attachments,
            boolean recurring,
            String recurrenceRule,
            LocalDate nextRecurrenceDate,
            UUID parentRecurrenceId,
            String approvalStatus,
            UUID approvedBy,
            Instant approvedAt) {}

    public record CreateExpenseRequest(
            @NotNull UUID categoryId,
            UUID supplierId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            LocalDate expenseDate,
            String description,
            String paymentMethod,
            String recurrenceRule,
            String notes) {}

    // ── Income ────────────────────────────────────────────────────────────────

    public record IncomeResponse(
            UUID id,
            String incomeNumber,
            UUID categoryId,
            UUID customerId,
            BigDecimal amount,
            LocalDate receivedDate,
            String description,
            String source,
            String paymentMethod,
            List<String> attachments) {}

    public record CreateIncomeRequest(
            @NotNull UUID categoryId,
            UUID customerId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            LocalDate receivedDate,
            String description,
            String source,
            String paymentMethod) {}
}
