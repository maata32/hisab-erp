package com.minierp.customer.internal;

import com.minierp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "suppliers",
        uniqueConstraints = @UniqueConstraint(name = "uk_suppliers_tenant_code", columnNames = {"tenant_id", "code"}),
        indexes = {
                @Index(name = "idx_suppliers_tenant", columnList = "tenant_id"),
                @Index(name = "idx_suppliers_phone", columnList = "phone")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class Supplier extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 50)
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

    @Column(name = "credit_limit", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
