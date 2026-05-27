package com.minierp.uom.internal;

import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.uom.api.UomCategoryDto;
import com.minierp.uom.api.UomDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UomService {

    private final UomRepository uoms;
    private final UomCategoryRepository categories;

    @Transactional(readOnly = true)
    public List<UomCategoryDto> listCategories() {
        return categories.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(c -> new UomCategoryDto(c.getId(), c.getCode(), c.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UomDto> listAll() {
        var byCategoryId = categories.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(UomCategory::getId, c -> c));
        return uoms.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(u -> {
                    var c = byCategoryId.get(u.getCategoryId());
                    return new UomDto(u.getId(), u.getCategoryId(),
                            c == null ? null : c.getCode(),
                            u.getCode(), u.getName(),
                            u.getRatioToBase(), u.isBase(), u.getDecimalPlaces());
                })
                .toList();
    }

    @Transactional
    @CacheEvict(value = {"uoms:byId", "uoms:byCode"}, allEntries = true)
    public UomCategoryDto createCategory(String code, String name) {
        if (categories.existsByCode(code)) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", code));
        }
        var c = UomCategory.builder().code(code).name(name).build();
        categories.save(c);
        return new UomCategoryDto(c.getId(), c.getCode(), c.getName());
    }

    @Transactional
    @CacheEvict(value = {"uoms:byId", "uoms:byCode"}, allEntries = true)
    public UomDto createUom(UUID categoryId, String code, String name, BigDecimal ratioToBase,
                            boolean isBase, int decimalPlaces) {
        var category = categories.findById(categoryId)
                .orElseThrow(() -> NotFoundException.of("entity.uom_category", categoryId));
        if (uoms.existsByCode(code)) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", code));
        }
        if (isBase) {
            uoms.findByCategoryIdAndIsBaseTrue(categoryId).ifPresent(existing -> {
                throw new BusinessException("error.uom.duplicate_base",
                        Map.of("category", category.getCode()));
            });
            ratioToBase = BigDecimal.ONE;
        }
        if (ratioToBase.signum() <= 0) {
            throw new BusinessException("error.uom.invalid_ratio", Map.of());
        }
        var u = Uom.builder()
                .categoryId(categoryId)
                .code(code)
                .name(name)
                .ratioToBase(ratioToBase)
                .isBase(isBase)
                .decimalPlaces(decimalPlaces)
                .build();
        uoms.save(u);
        return new UomDto(u.getId(), u.getCategoryId(), category.getCode(),
                u.getCode(), u.getName(),
                u.getRatioToBase(), u.isBase(), u.getDecimalPlaces());
    }
}
