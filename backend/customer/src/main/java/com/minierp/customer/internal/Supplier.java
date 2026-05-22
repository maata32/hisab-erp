package com.minierp.customer.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Supplier view over the unified {@code parties} table. A party row is visible
 * through this entity when {@code is_supplier = true}. The same party row may
 * also be a {@link Customer} when {@code is_customer = true}.
 */
@Entity
@Table(name = "parties")
@SQLRestriction("is_supplier = true")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Supplier extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "supplier_code", nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CustomerType type = CustomerType.COMPANY;

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

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MRU";

    @Column(length = 500)
    private String notes;

    @Column(name = "supplier_credit_limit", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** Role flag: true for parties visible as suppliers. Always true through this view. */
    @Column(name = "is_supplier", nullable = false)
    @Builder.Default
    private boolean isSupplier = true;

    /** Role flag: true when the same party row is also a customer. */
    @Column(name = "is_customer", nullable = false)
    @Builder.Default
    private boolean alsoCustomer = false;

    /** Populated only when {@link #alsoCustomer} is true. Read-only through this view. */
    @Column(name = "customer_code", insertable = false, updatable = false, length = 50)
    private String customerCode;

    @PrePersist
    void enforceSupplierRoleOnInsert() {
        this.isSupplier = true;
    }
}
