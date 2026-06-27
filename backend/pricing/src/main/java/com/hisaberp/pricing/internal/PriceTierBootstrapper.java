package com.hisaberp.pricing.internal;

import com.hisaberp.shared.tenant.TenantContext;
import com.hisaberp.tenant.events.OrganizationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seeds the default price tiers for a new tenant. RETAIL is default, WHOLESALE is
 * disabled until a customer is assigned to it.
 *
 * The stored {@code name} is a French fallback; the admin UI localizes these built-in
 * codes (fr/ar/en) by code (see {@code priceTiers.label.*}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
class PriceTierBootstrapper {

    private final PriceTierRepository tiers;

    private static final List<Map.Entry<String, String>> DEFAULT_TIERS = List.of(
            Map.entry("RETAIL", "Détail / Comptoir"),
            Map.entry("WHOLESALE", "Gros"),
            Map.entry("VIP", "VIP / Fidélité")
    );

    @ApplicationModuleListener
    public void onOrgCreated(OrganizationCreatedEvent event) {
        try {
            TenantContext.set(event.organizationId());
            for (var entry : DEFAULT_TIERS) {
                tiers.save(PriceTier.builder()
                        .code(entry.getKey())
                        .name(entry.getValue())
                        .defaultTier("RETAIL".equals(entry.getKey()))
                        .build());
            }
            log.info("Seeded {} default price tiers for tenant {}",
                    DEFAULT_TIERS.size(), event.code());
        } finally {
            TenantContext.clear();
        }
    }
}
