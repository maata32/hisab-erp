package com.hisaberp.inventory.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_transfers",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_transfers_tenant_number",
                columnNames = {"tenant_id", "transfer_number"}),
        indexes = {
                @Index(name = "idx_stock_transfers_tenant", columnList = "tenant_id"),
                @Index(name = "idx_stock_transfers_from_wh", columnList = "from_warehouse_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class StockTransfer extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "transfer_number", nullable = false, length = 60)
    private String transferNumber;

    @Column(name = "from_warehouse_id", nullable = false, columnDefinition = "uuid")
    private UUID fromWarehouseId;

    @Column(name = "to_warehouse_id", nullable = false, columnDefinition = "uuid")
    private UUID toWarehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StockTransferStatus status = StockTransferStatus.DRAFT;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockTransferLine> lines = new ArrayList<>();
}
