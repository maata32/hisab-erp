package com.minierp.customer.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "supplier_balances",
        uniqueConstraints = @UniqueConstraint(name = "uk_supplier_balances_supplier", columnNames = {"tenant_id", "supplier_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class SupplierBalance extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "supplier_id", nullable = false, columnDefinition = "uuid")
    private UUID supplierId;

    @Column(name = "total_invoiced", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal totalInvoiced = BigDecimal.ZERO;

    @Column(name = "total_paid", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "balance", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;
}
