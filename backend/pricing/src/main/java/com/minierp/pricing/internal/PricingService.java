package com.minierp.pricing.internal;

import com.minierp.catalog.api.VariantLookup;
import com.minierp.catalog.api.VariantView;
import com.minierp.pricing.api.PriceTierDto;
import com.minierp.pricing.api.ProductPriceDto;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final PriceTierRepository tiers;
    private final ProductPriceRepository prices;
    private final VariantLookup variants;

    @Transactional(readOnly = true)
    public List<PriceTierDto> listTiers() {
        return tiers.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream().map(this::toTierDto).toList();
    }

    @Transactional
    public PriceTierDto createTier(String code, String name, Boolean defaultTier) {
        if (tiers.existsByCode(code)) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", code));
        }
        boolean isDefault = Boolean.TRUE.equals(defaultTier);
        if (isDefault) {
            tiers.findFirstByDefaultTierTrue().ifPresent(existing -> existing.setDefaultTier(false));
        }
        PriceTier t = PriceTier.builder().code(code).name(name).defaultTier(isDefault).build();
        tiers.save(t);
        return toTierDto(t);
    }

    /** All prices across the product's variants — feeds the per-variant pricing grid. */
    @Transactional(readOnly = true)
    public List<ProductPriceDto> listForProduct(UUID productId) {
        return prices.findByProductId(productId).stream().map(this::toPriceDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductPriceDto> listForVariant(UUID variantId) {
        return prices.findByVariantId(variantId).stream().map(this::toPriceDto).toList();
    }

    /**
     * Uniform pricing: upsert the same price row on every active variant of the product.
     * Used when the product carries one shared price for all variants.
     */
    @Transactional
    public List<ProductPriceDto> upsertProductPrice(UUID productId, UUID uomId, UUID tierId,
                                                    BigDecimal amount, String currency, Boolean taxInclusive,
                                                    LocalDate validFrom, LocalDate validTo, BigDecimal minQty) {
        validate(tierId, amount);
        List<VariantView> productVariants = variants.listByProduct(productId);
        if (productVariants.isEmpty()) {
            throw NotFoundException.of("entity.product", productId);
        }
        return productVariants.stream()
                .map(v -> upsert(v.id(), productId, uomId, tierId, amount, currency,
                        taxInclusive, validFrom, validTo, minQty))
                .toList();
    }

    /** Per-variant pricing: upsert a single variant's price row. */
    @Transactional
    public ProductPriceDto upsertVariantPrice(UUID variantId, UUID uomId, UUID tierId,
                                              BigDecimal amount, String currency, Boolean taxInclusive,
                                              LocalDate validFrom, LocalDate validTo, BigDecimal minQty) {
        validate(tierId, amount);
        VariantView v = variants.require(variantId);
        return upsert(variantId, v.productId(), uomId, tierId, amount, currency,
                taxInclusive, validFrom, validTo, minQty);
    }

    private void validate(UUID tierId, BigDecimal amount) {
        if (tiers.findById(tierId).isEmpty()) {
            throw NotFoundException.of("entity.price_tier", tierId);
        }
        if (amount == null || amount.signum() < 0) {
            throw new BusinessException("error.pricing.negative_amount", Map.of());
        }
    }

    private ProductPriceDto upsert(UUID variantId, UUID productId, UUID uomId, UUID tierId,
                                   BigDecimal amount, String currency, Boolean taxInclusive,
                                   LocalDate validFrom, LocalDate validTo, BigDecimal minQty) {
        LocalDate effectiveFrom = validFrom == null ? LocalDate.of(2000, 1, 1) : validFrom;
        String effectiveCurrency = currency == null ? "MRU" : currency;
        boolean effectiveTaxInclusive = Boolean.TRUE.equals(taxInclusive);

        ProductPrice p = prices
                .findForUpsert(variantId, uomId, tierId, effectiveFrom, minQty)
                .orElseGet(() -> ProductPrice.builder()
                        .variantId(variantId)
                        .productId(productId)
                        .uomId(uomId)
                        .priceTierId(tierId)
                        .validFrom(effectiveFrom)
                        .build());
        p.setProductId(productId);
        p.setAmount(amount);
        p.setCurrency(effectiveCurrency);
        p.setTaxInclusive(effectiveTaxInclusive);
        p.setValidTo(validTo);
        p.setMinQty(minQty);
        prices.save(p);
        return toPriceDto(p);
    }

    private PriceTierDto toTierDto(PriceTier t) {
        return new PriceTierDto(t.getId(), t.getCode(), t.getName(), t.isDefaultTier(), t.isActive());
    }

    private ProductPriceDto toPriceDto(ProductPrice p) {
        return new ProductPriceDto(p.getId(), p.getVariantId(), p.getProductId(), p.getUomId(),
                p.getPriceTierId(), p.getAmount(), p.getCurrency(), p.isTaxInclusive(),
                p.getValidFrom(), p.getValidTo(), p.getMinQty());
    }
}
