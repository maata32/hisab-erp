package com.minierp.inventory.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "warehouses",
        uniqueConstraints = @UniqueConstraint(name = "uk_warehouses_tenant_code",
                columnNames = {"tenant_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Warehouse extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    @Builder.Default
    private WarehouseType type = WarehouseType.MAIN;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultWarehouse = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
