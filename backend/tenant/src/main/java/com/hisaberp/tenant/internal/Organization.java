package com.hisaberp.tenant.internal;

import com.hisaberp.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organizations",
        uniqueConstraints = @UniqueConstraint(name = "uk_organizations_code", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class Organization extends AuditableEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(length = 500)
    private String address;

    @Column(length = 30)
    private String phone;

    @Column(length = 200)
    private String email;

    /** Organization type code — references {@link OrganizationType#getCode()} (configurable). */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String type = "BOUTIQUE";

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MRU";

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String locale = "fr";

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String timezone = "Africa/Nouakchott";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrganizationStatus status = OrganizationStatus.TRIAL;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "subscription_plan_id", columnDefinition = "uuid")
    private UUID subscriptionPlanId;

    /** The admin user created during self-service registration; promoted to TENANT_ADMIN on approval. */
    @Column(name = "primary_admin_user_id", columnDefinition = "uuid")
    private UUID primaryAdminUserId;

    /** When the tenant entered PAST_DUE; the grace period before SUSPENDED counts from here. */
    @Column(name = "past_due_since")
    private Instant pastDueSince;
}
