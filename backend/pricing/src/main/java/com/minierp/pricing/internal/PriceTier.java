package com.minierp.pricing.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "price_tiers",
        uniqueConstraints = @UniqueConstraint(name = "uk_price_tiers_tenant_code",
                columnNames = {"tenant_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PriceTier extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean defaultTier = false;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
