package com.hisaberp.pricing.internal;

import com.hisaberp.catalog.api.CatalogLookup;
import com.hisaberp.catalog.api.ProductSnapshot;
import com.hisaberp.catalog.api.VariantLookup;
import com.hisaberp.catalog.api.VariantView;
import com.hisaberp.pricing.api.PriceResolver;
import com.hisaberp.pricing.api.ResolvedPrice;
import com.hisaberp.shared.error.BusinessException;
import com.hisaberp.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class PriceResolverImpl implements PriceResolver {

    private final ProductPriceRepository prices;
    private final PriceTierRepository tiers;
    private final CatalogLookup catalog;
    private final VariantLookup variants;

    @Override
    @Transactional(readOnly = true)
    public ResolvedPrice resolve(UUID variantId, UUID uomId, UUID priceTierId,
                                 BigDecimal quantity, LocalDate date, BigDecimal unitDiscount) {
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("error.pricing.invalid_quantity", Map.of());
        }

        VariantView variant = variants.require(variantId);
        ProductSnapshot product = catalog.findProductById(variant.productId())
                .orElseThrow(() -> NotFoundException.of("entity.product", variant.productId()));

        UUID effectiveUomId = uomId == null ? variant.baseUomId() : uomId;
        UUID effectiveTierId = priceTierId == null ? defaultTierId() : priceTierId;
        UUID productId = variant.productId();

        ProductPrice match = pickBestVariant(variantId, effectiveUomId, effectiveTierId, quantity, effectiveDate)
                .or(() -> pickBestVariant(variantId, effectiveUomId, defaultTierId(), quantity, effectiveDate))
                .or(() -> pickBestProduct(productId, effectiveUomId, effectiveTierId, quantity, effectiveDate))
                .or(() -> pickBestProduct(productId, effectiveUomId, defaultTierId(), quantity, effectiveDate))
                .orElseThrow(() -> new BusinessException("error.pricing.no_price",
                        Map.of("variantId", variantId, "uomId", effectiveUomId)));

        BigDecimal unit = match.getAmount();
        if (unitDiscount != null && unitDiscount.signum() > 0) {
            unit = unit.subtract(unitDiscount);
            if (unit.signum() < 0) unit = BigDecimal.ZERO;
        }

        BigDecimal taxRate = product.defaultTaxRate() == null ? BigDecimal.ZERO : product.defaultTaxRate();
        BigDecimal subtotal;
        BigDecimal tax;
        BigDecimal total;

        if (match.isTaxInclusive()) {
            total = unit.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            BigDecimal divisor = BigDecimal.ONE.add(taxRate);
            subtotal = total.divide(divisor, 2, RoundingMode.HALF_UP);
            tax = total.subtract(subtotal);
        } else {
            subtotal = unit.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
            total = subtotal.add(tax);
        }

        return new ResolvedPrice(
                variantId, productId, effectiveUomId, effectiveTierId, unit, quantity,
                subtotal, taxRate, tax, total, match.getCurrency(), match.isTaxInclusive());
    }

    private Optional<ProductPrice> pickBestVariant(UUID variantId, UUID uomId, UUID tierId,
                                                   BigDecimal quantity, LocalDate date) {
        return pick(prices.findActiveByVariant(variantId, uomId, tierId, date), quantity);
    }

    private Optional<ProductPrice> pickBestProduct(UUID productId, UUID uomId, UUID tierId,
                                                   BigDecimal quantity, LocalDate date) {
        return pick(prices.findActiveByProduct(productId, uomId, tierId, date), quantity);
    }

    private Optional<ProductPrice> pick(List<ProductPrice> candidates, BigDecimal quantity) {
        return candidates.stream()
                .filter(p -> p.getMinQty() == null || quantity.compareTo(p.getMinQty()) >= 0)
                .findFirst();
    }

    private UUID defaultTierId() {
        return tiers.findFirstByDefaultTierTrue()
                .map(PriceTier::getId)
                .orElseThrow(() -> new BusinessException("error.pricing.no_default_tier", Map.of()));
    }
}
