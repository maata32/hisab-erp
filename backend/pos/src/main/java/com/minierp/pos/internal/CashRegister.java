package com.minierp.pos.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "cash_registers",
        uniqueConstraints = @UniqueConstraint(name = "uk_cash_registers_tenant_code",
                columnNames = {"tenant_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class CashRegister extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "warehouse_id", columnDefinition = "uuid", nullable = false)
    private UUID warehouseId;

    @Column(name = "default_price_tier_id", columnDefinition = "uuid")
    private UUID defaultPriceTierId;

    @Column(name = "receipt_width_mm", nullable = false)
    @Builder.Default
    private int receiptWidthMm = 80;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
