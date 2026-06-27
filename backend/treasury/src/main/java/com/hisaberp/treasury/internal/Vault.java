package com.hisaberp.treasury.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Central physical cash safe — one per tenant. Balance reflects the total cash
 * stored in the safe at any moment and is always mutated via paired movements.
 */
@Entity
@Table(name = "vaults",
        uniqueConstraints = @UniqueConstraint(name = "uk_vaults_tenant", columnNames = {"tenant_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Vault extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 200)
    @Builder.Default
    private String name = "Coffre central";

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MRU";

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
}
