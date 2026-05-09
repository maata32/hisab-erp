package com.minierp.uom.internal;

import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.uom.api.UomDto;
import com.minierp.uom.api.UomLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class UomLookupImpl implements UomLookup {

    private final UomRepository uoms;
    private final UomCategoryRepository categories;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "uoms:byId", unless = "#result == null")
    public Optional<UomDto> findById(UUID id) {
        return uoms.findById(id).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "uoms:byCode", unless = "#result == null")
    public Optional<UomDto> findByCode(String code) {
        return uoms.findByCode(code).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal convert(BigDecimal amount, UUID fromUomId, UUID toUomId) {
        if (fromUomId.equals(toUomId)) return amount;
        Uom from = uoms.findById(fromUomId)
                .orElseThrow(() -> NotFoundException.of("entity.uom", fromUomId));
        Uom to = uoms.findById(toUomId)
                .orElseThrow(() -> NotFoundException.of("entity.uom", toUomId));
        if (!from.getCategoryId().equals(to.getCategoryId())) {
            throw new BusinessException("error.uom.category_mismatch",
                    Map.of("from", from.getCode(), "to", to.getCode()));
        }
        BigDecimal inBase = amount.multiply(from.getRatioToBase());
        BigDecimal converted = inBase.divide(to.getRatioToBase(), 12, RoundingMode.HALF_UP);
        return converted.setScale(to.getDecimalPlaces(), RoundingMode.HALF_UP);
    }

    private UomDto toDto(Uom u) {
        var cat = categories.findById(u.getCategoryId())
                .orElseThrow(() -> NotFoundException.of("entity.uom_category", u.getCategoryId()));
        return new UomDto(u.getId(), u.getCategoryId(), cat.getCode(),
                u.getCode(), u.getName(),
                u.getRatioToBase(), u.isBase(), u.getDecimalPlaces());
    }
}
