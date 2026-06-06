package com.minierp.payment.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payments_tenant_number", columnNames = {"tenant_id", "number"}),
        indexes = {
                @Index(name = "idx_payments_party", columnList = "party_id"),
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_date", columnList = "payment_date")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Payment extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 30)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PaymentType type;

    // Nullable: an expense payment (party-less CASH_OUT settling an EXPENSE
    // allocation) carries no customer/supplier. Partner payments still require a
    // party — enforced in PaymentService.create().
    @Column(name = "party_id", columnDefinition = "uuid")
    private UUID partyId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    @Builder.Default private String currency = "MRU";

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "bank_account", length = 100)
    private String bankAccount;

    /** Treasury bank account this payment debits/credits on confirm (non-cash
     *  methods). NULL for cash (→ vault) and paper settlements. */
    @Column(name = "bank_account_id", columnDefinition = "uuid")
    private UUID bankAccountId;

    @Column(name = "session_id", columnDefinition = "uuid")
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.DRAFT;

    @Column(length = 1000)
    private String notes;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "confirmed_by", columnDefinition = "uuid")
    private UUID confirmedBy;
}
