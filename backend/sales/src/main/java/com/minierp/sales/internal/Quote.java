package com.minierp.sales.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "quotes",
        uniqueConstraints = @UniqueConstraint(name = "uk_quotes_tenant_number", columnNames = {"tenant_id", "number"}),
        indexes = {
                @Index(name = "idx_quotes_customer", columnList = "customer_id"),
                @Index(name = "idx_quotes_status", columnList = "status")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Quote extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "customer_id", nullable = false, columnDefinition = "uuid")
    private UUID customerId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MRU";

    @Column(precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "converted_to_order_id", columnDefinition = "uuid")
    private UUID convertedToOrderId;

    @Column(length = 1000)
    private String notes;
}
