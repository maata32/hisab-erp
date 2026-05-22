package com.minierp.customer.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Accounts-receivable balance row backing the {@code ar_balances} table.
 * Keyed by the party id (one row per party with the customer role).
 */
@Entity
@Table(name = "ar_balances",
        uniqueConstraints = @UniqueConstraint(name = "uk_ar_balances_party", columnNames = {"tenant_id", "party_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class CustomerBalance extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "party_id", nullable = false, columnDefinition = "uuid")
    private UUID partyId;

    @Column(name = "total_invoiced", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal totalInvoiced = BigDecimal.ZERO;

    @Column(name = "total_paid", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "balance", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "overdue_amount", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal overdueAmount = BigDecimal.ZERO;

    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;
}
