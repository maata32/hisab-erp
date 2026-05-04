package com.minierp.identity.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens",
        indexes = @Index(name = "idx_password_reset_tokens_token_hash", columnList = "token_hash", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class PasswordResetToken extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;
}
