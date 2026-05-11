package com.minierp.expense.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "expenses",
        uniqueConstraints = @UniqueConstraint(name = "uk_expenses_tenant_number",
                columnNames = {"tenant_id", "expense_number"}),
        indexes = {
                @Index(name = "idx_expenses_tenant_date", columnList = "tenant_id,expense_date"),
                @Index(name = "idx_expenses_category", columnList = "category_id"),
                @Index(name = "idx_expenses_next_recurrence", columnList = "next_recurrence_date")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Expense extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "expense_number", nullable = false, length = 60)
    private String expenseNumber;

    @Column(name = "category_id", nullable = false, columnDefinition = "uuid")
    private UUID categoryId;

    @Column(name = "supplier_id", columnDefinition = "uuid")
    private UUID supplierId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    @Builder.Default
    private ExpensePaymentMethod paymentMethod = ExpensePaymentMethod.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private ExpensePaymentStatus paymentStatus = ExpensePaymentStatus.UNPAID;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> attachments;

    @Column(nullable = false)
    @Builder.Default
    private boolean recurring = false;

    @Column(name = "recurrence_rule", length = 500)
    private String recurrenceRule;

    @Column(name = "next_recurrence_date")
    private LocalDate nextRecurrenceDate;

    @Column(name = "parent_recurrence_id", columnDefinition = "uuid")
    private UUID parentRecurrenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus approvalStatus = ApprovalStatus.NOT_REQUIRED;

    @Column(name = "approved_by", columnDefinition = "uuid")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;
}
