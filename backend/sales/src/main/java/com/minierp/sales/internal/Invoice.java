package com.minierp.sales.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoices",
        uniqueConstraints = @UniqueConstraint(name = "uk_invoices_tenant_number", columnNames = {"tenant_id", "number"}),
        indexes = {
                @Index(name = "idx_invoices_customer", columnList = "customer_id"),
                @Index(name = "idx_invoices_status", columnList = "status"),
                @Index(name = "idx_invoices_due_date", columnList = "due_date")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Invoice extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "customer_id", nullable = false, columnDefinition = "uuid")
    private UUID customerId;

    @Column(name = "order_id", columnDefinition = "uuid")
    private UUID orderId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

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

    @Column(name = "paid_amount", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "balance", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(length = 1000)
    private String notes;
}
