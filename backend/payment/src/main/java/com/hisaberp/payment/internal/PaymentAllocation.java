package com.hisaberp.payment.internal;

import com.hisaberp.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payment_allocations",
        indexes = {
                @Index(name = "idx_pay_alloc_payment", columnList = "payment_id"),
                @Index(name = "idx_pay_alloc_target", columnList = "target_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class PaymentAllocation extends AuditableEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "payment_id", nullable = false, columnDefinition = "uuid")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30)
    private AllocationTargetType targetType;

    @Column(name = "target_id", nullable = false, columnDefinition = "uuid")
    private UUID targetId;

    @Column(name = "allocated_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal allocatedAmount;

    @Column(length = 500)
    private String notes;
}
