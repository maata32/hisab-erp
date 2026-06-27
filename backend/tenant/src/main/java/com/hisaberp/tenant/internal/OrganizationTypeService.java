package com.hisaberp.tenant.internal;

import com.hisaberp.shared.error.ConflictException;
import com.hisaberp.shared.error.NotFoundException;
import com.hisaberp.shared.error.ValidationException;
import com.hisaberp.tenant.api.OrganizationTypeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD for the configurable organization types (super-admin). Global reference data —
 * no tenant scoping. Conventions mirror the UoM CRUD: code is immutable and a type
 * still referenced by an organization cannot be deleted (deactivate it instead).
 */
@Service
@RequiredArgsConstructor
public class OrganizationTypeService {

    private final OrganizationTypeRepository types;
    private final OrganizationRepository orgs;

    @Transactional(readOnly = true)
    public List<OrganizationTypeDto> list(boolean activeOnly) {
        List<OrganizationType> rows = activeOnly
                ? types.findAllByActiveTrueOrderBySortOrderAscLabelAsc()
                : types.findAllByOrderBySortOrderAscLabelAsc();
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional
    public OrganizationTypeDto create(OrganizationTypeDto.CreateRequest req) {
        String code = req.code().trim().toUpperCase();
        if (types.existsByCode(code)) {
            throw new ConflictException("error.data_integrity", Map.of("field", "code", "value", code));
        }
        OrganizationType t = OrganizationType.builder()
                .code(code)
                .label(req.label().trim())
                .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
                .active(true)
                .build();
        return toDto(types.save(t));
    }

    @Transactional
    public OrganizationTypeDto update(UUID id, OrganizationTypeDto.UpdateRequest req) {
        OrganizationType t = load(id);
        // Code is immutable (organizations reference it). Only label / order / active are editable.
        if (req.label() != null && !req.label().isBlank()) t.setLabel(req.label().trim());
        if (req.sortOrder() != null) t.setSortOrder(req.sortOrder());
        if (req.active() != null) t.setActive(req.active());
        return toDto(t);
    }

    @Transactional
    public void delete(UUID id) {
        OrganizationType t = load(id);
        long inUse = orgs.countByType(t.getCode());
        if (inUse > 0) {
            throw new ValidationException("organization_type.in_use", Map.of("count", inUse));
        }
        types.delete(t);
    }

    /** Validates a type code on org creation: must exist and be active. Returns the canonical code. */
    @Transactional(readOnly = true)
    public String requireActiveCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("organization_type.required", Map.of());
        }
        String c = code.trim().toUpperCase();
        OrganizationType t = types.findByCode(c)
                .orElseThrow(() -> new ValidationException("organization_type.unknown", Map.of("value", code)));
        if (!t.isActive()) {
            throw new ValidationException("organization_type.inactive", Map.of("value", code));
        }
        return t.getCode();
    }

    private OrganizationType load(UUID id) {
        return types.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.organization_type", id));
    }

    private OrganizationTypeDto toDto(OrganizationType t) {
        return new OrganizationTypeDto(t.getId(), t.getCode(), t.getLabel(), t.getSortOrder(), t.isActive());
    }
}
