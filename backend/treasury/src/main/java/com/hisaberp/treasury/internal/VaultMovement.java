package com.hisaberp.treasury.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable ledger line for the central vault. Signed {@code amount} (positive = inflow).
 * Every movement points back to its counterpart on the other side (POS session, bank txn).
 */
@Entity
@Table(name = "vault_movements",
        indexes = {
                @Index(name = "idx_vault_movements_vault", columnList = "vault_id"),
                @Index(name = "idx_vault_movements_occurred", columnList = "occurred_at"),
                @Index(name = "idx_vault_movements_ref", columnList = "reference_type,reference_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class VaultMovement extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "vault_id", nullable = false, columnDefinition = "uuid")
    private UUID vaultId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VaultMovementType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id", columnDefinition = "uuid")
    private UUID referenceId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(length = 1000)
    private String note;
}
