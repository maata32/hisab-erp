package com.minierp.customer.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Customer view over the unified {@code parties} table. A party row is visible
 * through this entity when {@code is_customer = true}. The same party row may
 * also expose itself as a {@link Supplier} when {@code is_supplier = true} —
 * see the activate-role workflow.
 */
@Entity
@Table(name = "parties")
@SQLRestriction("is_customer = true")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Customer extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "customer_code", nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerType type = CustomerType.INDIVIDUAL;

    @Column(nullable = false, length = 250)
    private String name;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 500)
    private String address;

    @Column(name = "customer_credit_limit", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MRU";

    @Column(length = 500)
    private String notes;

    @Column(name = "default_price_tier_id", columnDefinition = "uuid")
    private UUID defaultPriceTierId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notification_preferences", columnDefinition = "jsonb")
    private String notificationPreferences;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Role flag: true for parties visible as customers. Always true through this view. */
    @Column(name = "is_customer", nullable = false)
    @Builder.Default
    private boolean isCustomer = true;

    /** Role flag: true when the same party row is also a supplier. */
    @Column(name = "is_supplier", nullable = false)
    @Builder.Default
    private boolean alsoSupplier = false;

    @PrePersist
    void enforceCustomerRoleOnInsert() {
        this.isCustomer = true;
    }
}
