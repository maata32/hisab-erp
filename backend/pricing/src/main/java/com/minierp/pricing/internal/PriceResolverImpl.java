package com.minierp.pricing.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.catalog.api.ProductSnapshot;
import com.minierp.pricing.api.PriceResolver;
import com.minierp.pricing.api.ResolvedPrice;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
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

    @Override
    @Transactional(readOnly = true)
    public ResolvedPrice resolve(UUID productId, UUID uomId, UUID priceTierId,
                                 BigDecimal quantity, LocalDate date, BigDecimal unitDiscount) {
        LocalDate effectiveDate = date == null ? LocalDate.now() : date;
        if (quantity == null || quantity.signum() <= 0) {
            throw new BusinessException("error.pricing.invalid_quantity", Map.of());
        }

        ProductSnapshot product = catalog.findProductById(productId)
                .orElseThrow(() -> NotFoundException.of("entity.product", productId));

        UUID effectiveUomId = uomId == null ? product.baseUomId() : uomId;
        UUID effectiveTierId = priceTierId == null ? defaultTierId() : priceTierId;

        ProductPrice match = pickBest(productId, effectiveUomId, effectiveTierId, quantity, effectiveDate)
                .or(() -> pickBest(productId, effectiveUomId, defaultTierId(), quantity, effectiveDate))
                .orElseThrow(() -> new BusinessException("error.pricing.no_price",
                        Map.of("productId", productId, "uomId", effectiveUomId)));

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
                productId, effectiveUomId, effectiveTierId, unit, quantity,
                subtotal, taxRate, tax, total, match.getCurrency(), match.isTaxInclusive());
    }

    private Optional<ProductPrice> pickBest(UUID productId, UUID uomId, UUID tierId,
                                            BigDecimal quantity, LocalDate date) {
        List<ProductPrice> candidates = prices.findActive(productId, uomId, tierId, date);
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
