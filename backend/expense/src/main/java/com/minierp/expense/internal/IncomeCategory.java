package com.minierp.expense.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "income_categories",
        indexes = @Index(name = "idx_income_categories_tenant", columnList = "tenant_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class IncomeCategory extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
