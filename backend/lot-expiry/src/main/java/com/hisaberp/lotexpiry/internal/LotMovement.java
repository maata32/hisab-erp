package com.hisaberp.lotexpiry.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "lot_movements",
        indexes = @Index(name = "idx_lot_movements_lot", columnList = "lot_id"))
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
class LotMovement extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "lot_id", nullable = false, columnDefinition = "uuid")
    private UUID lotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LotMovementType type;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(name = "uom_id", nullable = false, columnDefinition = "uuid")
    private UUID uomId;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id", columnDefinition = "uuid")
    private UUID referenceId;

    @Column(length = 500)
    private String notes;
}
