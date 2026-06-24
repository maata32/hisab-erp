package com.minierp.inventory.internal;

import com.minierp.inventory.api.WarehouseDto;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.persistence.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouses;

    @Transactional(readOnly = true)
    public List<WarehouseDto> list() {
        return warehouses.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseDto get(UUID id) {
        return toDto(loadWarehouseInTenant(id));
    }

    /**
     * Load a warehouse by id, enforcing it belongs to the current tenant. {@code findById}
     * bypasses the Hibernate tenant filter, so without this guard a token from tenant A could
     * read/modify a warehouse of tenant B (BUG-2 / SEC-02).
     */
    private Warehouse loadWarehouseInTenant(UUID id) {
        return TenantGuard.requireSameTenant(warehouses.findById(id),
                () -> NotFoundException.of("entity.warehouse", id));
    }

    @Transactional
    public WarehouseDto create(String code, String name, String address, String phone, Boolean isDefault) {
        if (warehouses.existsByCode(code)) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", code));
        }
        boolean def = Boolean.TRUE.equals(isDefault);
        if (def) {
            warehouses.findFirstByDefaultWarehouseTrue().ifPresent(w -> w.setDefaultWarehouse(false));
        }
        Warehouse w = Warehouse.builder()
                .code(code).name(name).address(address).phone(phone)
                .defaultWarehouse(def)
                .build();
        warehouses.save(w);
        return toDto(w);
    }

    /**
     * Promotes a warehouse to be the tenant's default, demoting the current
     * default. Idempotent when the target is already the default. There is
     * always exactly one default, so this never clears the default outright.
     */
    @Transactional
    public WarehouseDto setDefault(UUID id) {
        Warehouse w = loadWarehouseInTenant(id);
        if (w.isDefaultWarehouse()) {
            return toDto(w);
        }
        warehouses.findFirstByDefaultWarehouseTrue()
                .ifPresent(current -> current.setDefaultWarehouse(false));
        w.setDefaultWarehouse(true);
        return toDto(w);
    }

    @Transactional
    public WarehouseDto update(UUID id, String name, String address, String phone, Boolean active) {
        Warehouse w = loadWarehouseInTenant(id);
        if (name != null) w.setName(name);
        if (address != null) w.setAddress(address);
        if (phone != null) w.setPhone(phone);
        if (active != null) w.setActive(active);
        return toDto(w);
    }

    private WarehouseDto toDto(Warehouse w) {
        return new WarehouseDto(w.getId(), w.getCode(), w.getName(),
                w.getAddress(), w.getPhone(),
                w.getType() != null ? w.getType().name() : null,
                w.isDefaultWarehouse(), w.isActive());
    }
}
