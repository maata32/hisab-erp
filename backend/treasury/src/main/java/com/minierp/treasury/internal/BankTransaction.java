package com.minierp.treasury.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable ledger line for a bank account. Signed {@code amount} (positive = inflow).
 * Paired with a {@link VaultMovement} for deposit/withdrawal operations.
 */
@Entity
@Table(name = "bank_transactions",
        indexes = {
                @Index(name = "idx_bank_txn_account", columnList = "bank_account_id"),
                @Index(name = "idx_bank_txn_occurred", columnList = "occurred_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class BankTransaction extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "bank_account_id", nullable = false, columnDefinition = "uuid")
    private UUID bankAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BankTransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "vault_movement_id", columnDefinition = "uuid")
    private UUID vaultMovementId;

    @Column(length = 200)
    private String reference;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(length = 1000)
    private String note;
}
