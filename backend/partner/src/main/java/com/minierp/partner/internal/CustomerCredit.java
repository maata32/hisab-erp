package com.minierp.partner.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer_credits",
        indexes = {
                @Index(name = "idx_customer_credits_party", columnList = "party_id"),
                @Index(name = "idx_customer_credits_status", columnList = "status")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class CustomerCredit extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "party_id", nullable = false, columnDefinition = "uuid")
    private UUID partyId;

    @Column(name = "initial_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal initialAmount;

    @Column(name = "remaining_amount", precision = 19, scale = 2, nullable = false)
    private BigDecimal remainingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CreditSource source;

    @Column(name = "source_payment_id", columnDefinition = "uuid")
    private UUID sourcePaymentId;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerCreditStatus status = CustomerCreditStatus.ACTIVE;

    @Column(length = 500)
    private String notes;
}
