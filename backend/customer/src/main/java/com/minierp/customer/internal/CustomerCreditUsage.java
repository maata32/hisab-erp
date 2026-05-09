package com.minierp.customer.internal;

import com.minierp.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "customer_credit_usages",
        indexes = @Index(name = "idx_credit_usages_credit", columnList = "customer_credit_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class CustomerCreditUsage extends AuditableEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "customer_credit_id", nullable = false, columnDefinition = "uuid")
    private UUID customerCreditId;

    @Column(name = "payment_id", nullable = false, columnDefinition = "uuid")
    private UUID paymentId;

    @Column(name = "amount_used", precision = 19, scale = 2, nullable = false)
    private BigDecimal amountUsed;
}
