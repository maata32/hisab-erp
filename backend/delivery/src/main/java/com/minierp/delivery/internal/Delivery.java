package com.minierp.delivery.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "deliveries",
        uniqueConstraints = @UniqueConstraint(name = "uk_deliveries_tenant_number", columnNames = {"tenant_id", "number"}),
        indexes = {
                @Index(name = "idx_deliveries_party", columnList = "party_id"),
                @Index(name = "idx_deliveries_invoice", columnList = "invoice_id"),
                @Index(name = "idx_deliveries_status", columnList = "status")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Delivery extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 30)
    private String number;

    @Column(name = "party_id", nullable = false, columnDefinition = "uuid")
    private UUID partyId;

    @Column(name = "invoice_id", nullable = false, columnDefinition = "uuid")
    private UUID invoiceId;

    @Column(name = "warehouse_id", columnDefinition = "uuid")
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DeliveryStatus status = DeliveryStatus.PENDING;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "delivered_by", columnDefinition = "uuid")
    private UUID deliveredBy;

    @Column(name = "signed_by", length = 200)
    private String signedBy;

    @Column(length = 500)
    private String address;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    @Column(length = 1000)
    private String notes;
}
