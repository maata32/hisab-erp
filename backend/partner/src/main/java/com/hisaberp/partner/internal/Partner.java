package com.hisaberp.partner.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Unified counterparty entity (Odoo-style). One row represents a legal entity that
 * may carry the customer role ({@code isCustomer}), the supplier role
 * ({@code isSupplier}), or both. Role-specific data (code series, credit limits)
 * lives on dedicated columns and is sparse — non-null only when the role is active.
 */
@Entity
@Table(name = "parties")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Partner extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PartnerType type = PartnerType.COMPANY;

    @Column(nullable = false, length = 250)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(name = "tax_id", length = 50)
    private String taxId;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Column(name = "customer_credit_limit", precision = 19, scale = 2)
    private BigDecimal customerCreditLimit;

    @Column(name = "supplier_credit_limit", precision = 19, scale = 2)
    private BigDecimal supplierCreditLimit;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MRU";

    @Column(name = "default_price_tier_id", columnDefinition = "uuid")
    private UUID defaultPriceTierId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_preferences", columnDefinition = "jsonb")
    private String notificationPreferences;

    @Column(length = 500)
    private String notes;

    @Column(name = "is_customer", nullable = false)
    @Builder.Default
    private boolean isCustomer = false;

    @Column(name = "is_supplier", nullable = false)
    @Builder.Default
    private boolean isSupplier = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
