package com.minierp.expense.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "incomes",
        uniqueConstraints = @UniqueConstraint(name = "uk_incomes_tenant_number",
                columnNames = {"tenant_id", "income_number"}),
        indexes = @Index(name = "idx_incomes_tenant_date", columnList = "tenant_id,received_date"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Income extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "income_number", nullable = false, length = 60)
    private String incomeNumber;

    @Column(name = "category_id", nullable = false, columnDefinition = "uuid")
    private UUID categoryId;

    @Column(name = "party_id", columnDefinition = "uuid")
    private UUID partyId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(length = 1000)
    private String description;

    @Column(length = 500)
    private String source;

    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> attachments;
}
