package com.minierp.tenant.internal;

import com.minierp.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Configurable organization type (super-admin managed). Global reference table, like
 * {@link SubscriptionPlan} — no tenant scoping. {@code Organization.type} stores the
 * {@code code} (a varchar), so existing data needs no migration.
 */
@Entity
@Table(name = "organization_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_organization_types_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class OrganizationType extends AuditableEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
