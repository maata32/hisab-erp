package com.hisaberp.expense.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "expense_categories",
        indexes = @Index(name = "idx_expense_categories_tenant", columnList = "tenant_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class ExpenseCategory extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    @Column(length = 20)
    private String color;

    /** Daily spend cap; a new expense pushing the category's day-to-date total
     *  over this value is created PENDING approval. NULL = no daily cap. */
    @Column(name = "daily_limit", precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    /** Monthly spend cap; same rule as {@link #dailyLimit} over the calendar month.
     *  NULL = no monthly cap. */
    @Column(name = "monthly_limit", precision = 19, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
