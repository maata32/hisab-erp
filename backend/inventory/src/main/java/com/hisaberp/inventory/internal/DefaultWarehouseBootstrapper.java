package com.hisaberp.inventory.internal;

import com.hisaberp.shared.tenant.TenantContext;
import com.hisaberp.tenant.events.OrganizationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class DefaultWarehouseBootstrapper {

    private final WarehouseRepository warehouses;

    @ApplicationModuleListener
    public void onOrgCreated(OrganizationCreatedEvent event) {
        try {
            TenantContext.set(event.organizationId());
            warehouses.save(Warehouse.builder()
                    .code("MAIN")
                    .name("Main warehouse")
                    .defaultWarehouse(true)
                    .build());
            log.info("Seeded default warehouse for tenant {}", event.code());
        } finally {
            TenantContext.clear();
        }
    }
}
