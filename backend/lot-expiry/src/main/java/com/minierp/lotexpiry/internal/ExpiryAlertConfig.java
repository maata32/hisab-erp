package com.minierp.lotexpiry.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "expiry_alert_configs",
        indexes = @Index(name = "idx_expiry_alert_configs_tenant", columnList = "tenant_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class ExpiryAlertConfig extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "product_category_id", columnDefinition = "uuid")
    private UUID productCategoryId;

    @Column(name = "days_before_expiry", nullable = false)
    private int daysBeforeExpiry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AlertSeverity severity = AlertSeverity.WARNING;

    /** Comma-separated role names, e.g. "MANAGER,STOCK_KEEPER" */
    @Column(name = "notify_roles", length = 500)
    private String notifyRoles;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
