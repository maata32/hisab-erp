package com.minierp.catalog.internal;

import com.minierp.catalog.api.ProductCategoryDto;
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
public class ProductCategoryService {

    private final ProductCategoryRepository categories;

    @Transactional(readOnly = true)
    public List<ProductCategoryDto> tree() {
        return categories.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public ProductCategoryDto create(String code, String name, UUID parentId, Integer sortOrder) {
        if (categories.existsByCode(code)) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", code));
        }
        if (parentId != null && categories.findById(parentId).isEmpty()) {
            throw NotFoundException.of("entity.product_category", parentId);
        }
        ProductCategory c = ProductCategory.builder()
                .code(code).name(name).parentId(parentId)
                .sortOrder(sortOrder == null ? 0 : sortOrder)
                .build();
        categories.save(c);
        return toDto(c);
    }

    @Transactional
    public ProductCategoryDto update(UUID id, String name, Integer sortOrder, Boolean active) {
        ProductCategory c = categories.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.product_category", id));
        if (name != null) c.setName(name);
        if (sortOrder != null) c.setSortOrder(sortOrder);
        if (active != null) c.setActive(active);
        return toDto(c);
    }

    private ProductCategoryDto toDto(ProductCategory c) {
        return new ProductCategoryDto(c.getId(), c.getCode(), c.getName(),
                c.getParentId(), c.getSortOrder(), c.isActive());
    }
}
