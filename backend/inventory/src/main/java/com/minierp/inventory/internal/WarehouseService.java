package com.minierp.inventory.internal;

import com.minierp.inventory.api.WarehouseDto;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
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
        return warehouses.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseDto get(UUID id) {
        return toDto(warehouses.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.warehouse", id)));
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

    @Transactional
    public WarehouseDto update(UUID id, String name, String address, String phone, Boolean active) {
        Warehouse w = warehouses.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.warehouse", id));
        if (name != null) w.setName(name);
        if (address != null) w.setAddress(address);
        if (phone != null) w.setPhone(phone);
        if (active != null) w.setActive(active);
        return toDto(w);
    }

    private WarehouseDto toDto(Warehouse w) {
        return new WarehouseDto(w.getId(), w.getCode(), w.getName(),
                w.getAddress(), w.getPhone(), w.isDefaultWarehouse(), w.isActive());
    }
}
