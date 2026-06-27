package com.hisaberp.catalog.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hisaberp.catalog.api.VariantLookup;
import com.hisaberp.catalog.api.VariantView;
import com.hisaberp.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class VariantLookupImpl implements VariantLookup {

    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<VariantView> findById(UUID variantId) {
        return variants.findById(variantId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VariantView> listByProduct(UUID productId) {
        return variants.findByProductIdOrderBySkuAsc(productId).stream()
                .filter(ProductVariant::isActive)
                .map(this::toView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<VariantView> findDefaultForProduct(UUID productId) {
        return variants.findFirstByProductIdAndDefaultVariantTrue(productId).map(this::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public VariantView require(UUID variantId) {
        return findById(variantId)
                .orElseThrow(() -> NotFoundException.of("entity.product_variant", variantId));
    }

    private VariantView toView(ProductVariant v) {
        Product p = products.findById(v.getProductId())
                .orElseThrow(() -> NotFoundException.of("entity.product", v.getProductId()));
        String attrsDisplay = attributesDisplay(v.getAttributes());
        String label = (attrsDisplay == null || attrsDisplay.isBlank())
                ? p.getName()
                : p.getName() + " — " + attrsDisplay;
        return new VariantView(v.getId(), v.getProductId(), v.getSku(), v.getBarcode(),
                p.getBaseUomId(), p.getName(), attrsDisplay, label, v.isDefaultVariant(), v.isActive());
    }

    /** Turn the {@code {"Couleur":"Rouge","Taille":"M"}} cache into "Rouge / M". */
    private String attributesDisplay(String attributesJson) {
        if (attributesJson == null || attributesJson.isBlank()) return null;
        try {
            Map<String, String> map = objectMapper.readValue(attributesJson, new TypeReference<LinkedHashMap<String, String>>() {});
            return String.join(" / ", map.values());
        } catch (Exception e) {
            return null;
        }
    }
}
