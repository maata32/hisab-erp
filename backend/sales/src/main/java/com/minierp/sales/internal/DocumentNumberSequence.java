package com.minierp.sales.internal;

import com.minierp.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "document_number_sequences",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_doc_seq_tenant_type_year",
                columnNames = {"tenant_id", "document_type", "year"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class DocumentNumberSequence extends AuditableEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false, columnDefinition = "uuid")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    @Builder.Default
    private long counter = 0L;

    @Column(nullable = false, length = 10)
    private String prefix;

    void increment() { this.counter++; }

    String format() {
        return String.format("%s-%d-%05d", prefix, year, counter);
    }

    static DocumentNumberSequence forType(UUID tenantId, DocumentType type, int year) {
        String prefix = switch (type) {
            case QUOTE -> "DEV";
            case ORDER -> "CMD";
            case INVOICE -> "FAC";
            case CREDIT_NOTE -> "AVO";
            case DELIVERY_NOTE -> "BL";
            case PAYMENT_RECEIPT -> "PAY";
        };
        return DocumentNumberSequence.builder()
                .tenantId(tenantId)
                .documentType(type)
                .year(year)
                .prefix(prefix)
                .counter(0L)
                .build();
    }
}
