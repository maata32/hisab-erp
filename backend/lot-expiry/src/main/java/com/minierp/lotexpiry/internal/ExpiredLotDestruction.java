package com.minierp.lotexpiry.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "expired_lot_destructions",
        indexes = @Index(name = "idx_eld_lot", columnList = "lot_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class ExpiredLotDestruction extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "lot_id", nullable = false, columnDefinition = "uuid")
    private UUID lotId;

    @Column(name = "destruction_date", nullable = false)
    private LocalDate destructionDate;

    @Column(name = "destroyed_by", columnDefinition = "uuid")
    private UUID destroyedBy;

    @Column(name = "quantity_destroyed", nullable = false, precision = 19, scale = 6)
    private BigDecimal quantityDestroyed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DestructionMethod method;

    @Column(precision = 19, scale = 6)
    @Builder.Default
    private BigDecimal cost = BigDecimal.ZERO;

    @Column(length = 1000)
    private String notes;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> attachments;
}
