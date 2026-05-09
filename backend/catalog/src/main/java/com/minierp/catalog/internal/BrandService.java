package com.minierp.catalog.internal;

import com.minierp.catalog.api.BrandDto;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brands;

    @Transactional(readOnly = true)
    public PageResponse<BrandDto> list(Pageable pageable) {
        return PageResponse.of(brands.findAll(pageable).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public BrandDto get(UUID id) {
        return toDto(brands.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.brand", id)));
    }

    @Transactional
    public BrandDto create(String code, String name, String description, String logoUrl) {
        if (brands.existsByCode(code)) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", code));
        }
        Brand b = Brand.builder()
                .code(code).name(name).description(description).logoUrl(logoUrl).build();
        brands.save(b);
        return toDto(b);
    }

    @Transactional
    public BrandDto update(UUID id, String name, String description, String logoUrl, Boolean active) {
        Brand b = brands.findById(id).orElseThrow(() -> NotFoundException.of("entity.brand", id));
        if (name != null) b.setName(name);
        if (description != null) b.setDescription(description);
        if (logoUrl != null) b.setLogoUrl(logoUrl);
        if (active != null) b.setActive(active);
        return toDto(b);
    }

    private BrandDto toDto(Brand b) {
        return new BrandDto(b.getId(), b.getCode(), b.getName(),
                b.getDescription(), b.getLogoUrl(), b.isActive());
    }
}
