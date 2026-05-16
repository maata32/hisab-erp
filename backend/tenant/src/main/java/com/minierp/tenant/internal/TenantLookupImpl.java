package com.minierp.tenant.internal;

import com.minierp.tenant.api.PlanLimits;
import com.minierp.tenant.api.TenantLookup;
import com.minierp.tenant.api.TenantSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class TenantLookupImpl implements TenantLookup {

    private final OrganizationRepository orgs;
    private final SubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;

    @Override
    @Cacheable(value = "tenants:byCode", unless = "#result == null")
    public Optional<TenantSnapshot> findByCode(String code) {
        return orgs.findByCode(code).map(this::toSnapshot);
    }

    @Override
    @Cacheable(value = "tenants:byId", unless = "#result == null")
    public Optional<TenantSnapshot> findById(UUID id) {
        return orgs.findById(id).map(this::toSnapshot);
    }

    @Override
    @Cacheable(value = "tenants:limits", unless = "#result == null")
    public PlanLimits findLimitsForTenant(UUID tenantId) {
        return subscriptions.findByOrganizationId(tenantId)
                .flatMap(s -> plans.findById(s.getPlanId()))
                .map(p -> new PlanLimits(
                        p.getMaxUsers(),
                        p.getMaxProducts(),
                        p.getMaxCashRegisters(),
                        p.getMaxProductImages()))
                .orElseGet(PlanLimits::defaults);
    }

    private TenantSnapshot toSnapshot(Organization o) {
        return new TenantSnapshot(
                o.getId(),
                o.getCode(),
                o.getName(),
                o.getType().name(),
                o.getStatus().name(),
                o.getCurrency(),
                o.getLocale(),
                o.getTimezone());
    }
}
