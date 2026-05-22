package com.minierp.sales.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "credit_notes",
        uniqueConstraints = @UniqueConstraint(name = "uk_credit_notes_tenant_number", columnNames = {"tenant_id", "number"}),
        indexes = @Index(name = "idx_credit_notes_invoice", columnList = "invoice_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class CreditNote extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "invoice_id", nullable = false, columnDefinition = "uuid")
    private UUID invoiceId;

    @Column(name = "party_id", nullable = false, columnDefinition = "uuid")
    private UUID partyId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CreditNoteStatus status = CreditNoteStatus.DRAFT;

    @Column(name = "applied_to_invoice_id", columnDefinition = "uuid")
    private UUID appliedToInvoiceId;

    @Column(name = "refunded_amount", precision = 19, scale = 2)
    @Builder.Default private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default private String currency = "MRU";
}
