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
@Table(name = "inventory_counts",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_counts_tenant_number",
                columnNames = {"tenant_id", "count_number"}),
        indexes = @Index(name = "idx_inventory_counts_warehouse", columnList = "warehouse_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class InventoryCount extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "count_number", nullable = false, length = 60)
    private String countNumber;

    @Column(name = "warehouse_id", nullable = false, columnDefinition = "uuid")
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InventoryCountStatus status = InventoryCountStatus.DRAFT;

    @Column(name = "count_date", nullable = false)
    private LocalDate countDate;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "validated_by", columnDefinition = "uuid")
    private UUID validatedBy;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "count", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InventoryCountLine> lines = new ArrayList<>();
}
