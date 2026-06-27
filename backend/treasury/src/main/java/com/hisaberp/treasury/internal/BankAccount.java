package com.hisaberp.treasury.internal;

import com.hisaberp.shared.persistence.TenantAwareEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts",
        indexes = @Index(name = "idx_bank_accounts_tenant", columnList = "tenant_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
class BankAccount extends TenantAwareEntity {

    @Id @GeneratedValue @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "bank_name", length = 200)
    private String bankName;

    @Column(name = "account_number", length = 100)
    private String accountNumber;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "MRU";

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
