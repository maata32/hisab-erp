package com.hisaberp.tenant.internal;

import com.hisaberp.shared.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "tenant_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_tenant_settings_org", columnNames = "organization_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
class TenantSettings extends AuditableEntity {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delivery_settings", columnDefinition = "jsonb")
    private Map<String, Object> deliverySettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pos_settings", columnDefinition = "jsonb")
    private Map<String, Object> posSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_settings", columnDefinition = "jsonb")
    private Map<String, Object> paymentSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "expiry_settings", columnDefinition = "jsonb")
    private Map<String, Object> expirySettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_settings", columnDefinition = "jsonb")
    private Map<String, Object> notificationSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "invoice_settings", columnDefinition = "jsonb")
    private Map<String, Object> invoiceSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pricing_settings", columnDefinition = "jsonb")
    private Map<String, Object> pricingSettings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_settings", columnDefinition = "jsonb")
    private Map<String, Object> customSettings;
}
