package com.minierp.catalog.internal;

import com.minierp.catalog.api.AttributeDto;
import com.minierp.catalog.api.AttributeDto.AttributeValueDto;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.persistence.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttributeService {

    private final AttributeRepository attributes;
    private final AttributeValueRepository values;
    private final ProductAttributeValueRepository productAttributeValues;
    private final VariantAttributeValueRepository variantAttributeValues;

    @Transactional(readOnly = true)
    public List<AttributeDto> list() {
        return attributes.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(this::toDto).toList();
    }

    @Transactional
    public AttributeDto create(SaveAttributeRequest req) {
        if (attributes.existsByNameIgnoreCase(req.name())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "name", "value", req.name()));
        }
        Attribute a = Attribute.builder()
                .name(req.name())
                .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
                .active(req.active() == null || req.active())
                .build();
        attributes.save(a);
        return toDto(a);
    }

    @Transactional
    public AttributeDto update(UUID id, SaveAttributeRequest req) {
        Attribute a = loadAttributeInTenant(id);
        if (req.name() != null) a.setName(req.name());
        if (req.sortOrder() != null) a.setSortOrder(req.sortOrder());
        if (req.active() != null) a.setActive(req.active());
        return toDto(a);
    }

    @Transactional
    public void delete(UUID id) {
        Attribute a = loadAttributeInTenant(id);
        for (AttributeValue v : values.findByAttributeIdOrderBySortOrderAscValueAsc(id)) {
            ensureValueUnused(v.getId());
        }
        values.deleteByAttributeId(id);
        attributes.delete(a);
    }

    @Transactional
    public AttributeValueDto addValue(UUID attributeId, SaveAttributeValueRequest req) {
        loadAttributeInTenant(attributeId);
        AttributeValue v = AttributeValue.builder()
                .attributeId(attributeId)
                .value(req.value())
                .code(blankToNull(req.code()))
                .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
                .active(req.active() == null || req.active())
                .build();
        values.save(v);
        return toValueDto(v);
    }

    @Transactional
    public AttributeValueDto updateValue(UUID attributeId, UUID valueId, SaveAttributeValueRequest req) {
        AttributeValue v = requireValue(attributeId, valueId);
        if (req.value() != null) v.setValue(req.value());
        if (req.code() != null) v.setCode(blankToNull(req.code()));
        if (req.sortOrder() != null) v.setSortOrder(req.sortOrder());
        if (req.active() != null) v.setActive(req.active());
        return toValueDto(v);
    }

    @Transactional
    public void deleteValue(UUID attributeId, UUID valueId) {
        AttributeValue v = requireValue(attributeId, valueId);
        ensureValueUnused(valueId);
        values.delete(v);
    }

    private void ensureValueUnused(UUID valueId) {
        if (productAttributeValues.existsByAttributeValueId(valueId)
                || variantAttributeValues.existsByAttributeValueId(valueId)) {
            throw new BusinessException("error.attribute_value.in_use", Map.of("valueId", valueId));
        }
    }

    private AttributeValue requireValue(UUID attributeId, UUID valueId) {
        // Validate the attribute belongs to the current tenant first; the value is then
        // confirmed to hang off that attribute, so it cannot be a foreign-tenant row.
        loadAttributeInTenant(attributeId);
        AttributeValue v = values.findById(valueId)
                .orElseThrow(() -> NotFoundException.of("entity.attribute_value", valueId));
        if (!v.getAttributeId().equals(attributeId)) {
            throw NotFoundException.of("entity.attribute_value", valueId);
        }
        return v;
    }

    /** Load an attribute by id, enforcing it belongs to the current tenant ({@code findById}
     *  bypasses the Hibernate tenant filter — BUG-2 / SEC-02). */
    private Attribute loadAttributeInTenant(UUID id) {
        return TenantGuard.requireSameTenant(attributes.findById(id),
                () -> NotFoundException.of("entity.attribute", id));
    }

    private AttributeDto toDto(Attribute a) {
        var vals = values.findByAttributeIdOrderBySortOrderAscValueAsc(a.getId()).stream()
                .map(this::toValueDto).toList();
        return new AttributeDto(a.getId(), a.getName(), a.getSortOrder(), a.isActive(), vals);
    }

    private AttributeValueDto toValueDto(AttributeValue v) {
        return new AttributeValueDto(v.getId(), v.getAttributeId(), v.getValue(),
                v.getCode(), v.getSortOrder(), v.isActive());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public record SaveAttributeRequest(String name, Integer sortOrder, Boolean active) {}

    public record SaveAttributeValueRequest(String value, String code, Integer sortOrder, Boolean active) {}
}
