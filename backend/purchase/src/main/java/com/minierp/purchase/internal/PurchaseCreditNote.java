package com.minierp.purchase.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Avoir d'achat (AVA) — supplier credit note. Mirror of the sales
 * {@code CreditNote}: total-only, issued against a single purchase invoice,
 * letters that invoice and (when goods had been received) triggers a RETURN
 * goods-receipt sending the stock back to the supplier.
 */
@Entity
@Table(name = "purchase_credit_notes",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_credit_notes_tenant_number", columnNames = {"tenant_id", "number"}),
        indexes = @Index(name = "idx_purchase_credit_notes_invoice", columnList = "purchase_invoice_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class PurchaseCreditNote extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "purchase_invoice_id", nullable = false, columnDefinition = "uuid")
    private UUID purchaseInvoiceId;

    @Column(name = "party_id", nullable = false, columnDefinition = "uuid")
    private UUID partyId;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(length = 500)
    private String reason;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default private BigDecimal amount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2, nullable = false)
    @Builder.Default private BigDecimal total = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseCreditNoteStatus status = PurchaseCreditNoteStatus.DRAFT;

    @Column(name = "applied_to_invoice_id", columnDefinition = "uuid")
    private UUID appliedToInvoiceId;

    @Column(nullable = false, length = 3)
    @Builder.Default private String currency = "MRU";
}
