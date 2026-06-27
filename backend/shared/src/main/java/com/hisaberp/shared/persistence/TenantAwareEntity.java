package com.hisaberp.shared.persistence;

import com.hisaberp.shared.tenant.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.util.UUID;

/**
 * Defense layer 1 of multi-tenant isolation.
 * Applies a Hibernate filter at SELECT time and stamps tenant_id at INSERT time
 * from {@link TenantContext}. PostgreSQL RLS is the second, mandatory layer (see {@code TenantRlsInterceptor}).
 */
@MappedSuperclass
@Getter
@Setter
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public abstract class TenantAwareEntity extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @PrePersist
    void prePersistStampTenant() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.require();
        }
    }
}
