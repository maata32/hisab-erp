package com.minierp.expense.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
