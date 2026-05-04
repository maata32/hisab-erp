package com.minierp.identity.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_tenant_email", columnNames = {"tenant_id", "email"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class User extends TenantAwareEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "preferred_language", nullable = false, length = 5)
    @Builder.Default
    private String preferredLanguage = "fr";

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_super_admin", nullable = false)
    @Builder.Default
    private boolean superAdmin = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at", nullable = false)
    @Builder.Default
    private Instant passwordChangedAt = Instant.now();

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
