package com.hisaberp.uom.internal;

import com.hisaberp.shared.error.BusinessException;
import com.hisaberp.shared.error.ConflictException;
import com.hisaberp.shared.error.NotFoundException;
import com.hisaberp.uom.api.UomCategoryDto;
import com.hisaberp.uom.api.UomView;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public List<UomView> listAll() {
        var byCategoryId = categories.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(UomCategory::getId, c -> c));
        Set<UUID> referenced = new HashSet<>(uoms.findReferencedUomIds());
        return uoms.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(u -> {
                    var c = byCategoryId.get(u.getCategoryId());
                    return new UomView(u.getId(), u.getCategoryId(),
                            c == null ? null : c.getCode(),
                            u.getCode(), u.getName(),
                            u.getRatioToBase(), u.isBase(), u.getDecimalPlaces(),
                            referenced.contains(u.getId()));
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
    public UomView createUom(UUID categoryId, String code, String name, BigDecimal ratioToBase,
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
        return toView(u, category.getCode(), false);
    }

    /**
     * Update a unit. {@code name} and {@code decimalPlaces} are always editable; {@code code},
     * {@code ratioToBase} and {@code isBase} may only change while the unit is unreferenced,
     * so historical document math is never rewritten.
     */
    @Transactional
    @CacheEvict(value = {"uoms:byId", "uoms:byCode"}, allEntries = true)
    public UomView updateUom(UUID id, String code, String name, BigDecimal ratioToBase,
                             boolean isBase, int decimalPlaces) {
        var u = uoms.findById(id).orElseThrow(() -> NotFoundException.of("entity.uom", id));
        var category = categories.findById(u.getCategoryId())
                .orElseThrow(() -> NotFoundException.of("entity.uom_category", u.getCategoryId()));

        boolean codeChanged = !u.getCode().equals(code);
        boolean baseChanged = u.isBase() != isBase;
        boolean ratioChanged = u.getRatioToBase().compareTo(ratioToBase) != 0;
        boolean referenced = uoms.isReferenced(id);

        if ((codeChanged || baseChanged || ratioChanged) && referenced) {
            throw new BusinessException("error.uom.immutable_in_use",
                    Map.of("code", u.getCode()));
        }

        if (codeChanged && uoms.existsByCode(code)) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", code));
        }
        if (isBase && baseChanged) {
            uoms.findByCategoryIdAndIsBaseTrue(u.getCategoryId()).ifPresent(existing -> {
                throw new BusinessException("error.uom.duplicate_base",
                        Map.of("category", category.getCode()));
            });
        }
        if (isBase) {
            ratioToBase = BigDecimal.ONE;
        }
        if (ratioToBase.signum() <= 0) {
            throw new BusinessException("error.uom.invalid_ratio", Map.of());
        }

        u.setCode(code);
        u.setName(name);
        u.setRatioToBase(ratioToBase);
        u.setBase(isBase);
        u.setDecimalPlaces(decimalPlaces);
        uoms.save(u);
        return toView(u, category.getCode(), referenced);
    }

    @Transactional
    @CacheEvict(value = {"uoms:byId", "uoms:byCode"}, allEntries = true)
    public void deleteUom(UUID id) {
        var u = uoms.findById(id).orElseThrow(() -> NotFoundException.of("entity.uom", id));
        if (uoms.isReferenced(id)) {
            throw new ConflictException("error.uom.in_use", Map.of("code", u.getCode()));
        }
        if (u.isBase() && uoms.countByCategoryId(u.getCategoryId()) > 1) {
            throw new BusinessException("error.uom.base_required", Map.of("code", u.getCode()));
        }
        uoms.delete(u);
    }

    @Transactional
    @CacheEvict(value = {"uoms:byId", "uoms:byCode"}, allEntries = true)
    public UomCategoryDto updateCategory(UUID id, String code, String name) {
        var c = categories.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.uom_category", id));
        if (!c.getCode().equals(code) && categories.existsByCode(code)) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", code));
        }
        c.setCode(code);
        c.setName(name);
        categories.save(c);
        return new UomCategoryDto(c.getId(), c.getCode(), c.getName());
    }

    @Transactional
    @CacheEvict(value = {"uoms:byId", "uoms:byCode"}, allEntries = true)
    public void deleteCategory(UUID id) {
        var c = categories.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.uom_category", id));
        if (uoms.existsByCategoryId(id)) {
            throw new ConflictException("error.uom.category_in_use",
                    Map.of("category", c.getCode()));
        }
        categories.delete(c);
    }

    private UomView toView(Uom u, String categoryCode, boolean inUse) {
        return new UomView(u.getId(), u.getCategoryId(), categoryCode,
                u.getCode(), u.getName(),
                u.getRatioToBase(), u.isBase(), u.getDecimalPlaces(), inUse);
    }
}
