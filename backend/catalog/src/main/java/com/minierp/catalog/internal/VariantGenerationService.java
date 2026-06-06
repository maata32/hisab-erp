package com.minierp.catalog.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns the rule that a product's variants are exactly the cartesian product of its
 * selected attribute values — and that an attribute-less product always keeps a single
 * default variant. Stale variants are deactivated (never deleted) so stock and document
 * lines that reference them stay intact.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class VariantGenerationService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductAttributeValueRepository productAttributeValues;
    private final VariantAttributeValueRepository variantAttributeValues;
    private final AttributeValueRepository attributeValues;
    private final AttributeRepository attributes;
    private final ObjectMapper objectMapper;

    /** Replace the product's enabled attribute values, then regenerate variants. */
    @Transactional
    public void setAttributeValues(UUID productId, List<UUID> valueIds) {
        Product product = products.findById(productId)
                .orElseThrow(() -> NotFoundException.of("entity.product", productId));
        List<UUID> distinct = valueIds == null ? List.of()
                : valueIds.stream().distinct().toList();
        // Validate every value exists before mutating.
        if (!distinct.isEmpty() && attributeValues.findByIdIn(distinct).size() != distinct.size()) {
            throw NotFoundException.of("entity.attribute_value", distinct);
        }
        productAttributeValues.deleteByProductId(productId);
        for (UUID valueId : distinct) {
            productAttributeValues.save(ProductAttributeValue.builder()
                    .productId(productId).attributeValueId(valueId).build());
        }
        regenerate(product);
    }

    /** Ensure an attribute-less product has exactly one default variant (used on create). */
    @Transactional
    public void ensureDefaultVariant(Product product) {
        regenerate(product);
    }

    /**
     * Sync the variant rows to match the product's current attribute-value selection.
     */
    void regenerate(Product product) {
        List<UUID> selectedValueIds = productAttributeValues.findByProductId(product.getId()).stream()
                .map(ProductAttributeValue::getAttributeValueId).toList();
        List<ProductVariant> existing = variants.findByProductId(product.getId());

        if (selectedValueIds.isEmpty()) {
            regenerateDefaultOnly(product, existing);
            return;
        }

        // Group selected values by attribute, preserving a stable attribute order.
        Map<UUID, AttributeValue> valueById = attributeValues.findByIdIn(selectedValueIds).stream()
                .collect(Collectors.toMap(AttributeValue::getId, v -> v));
        Map<UUID, String> attrNameById = attributes.findAllByOrderBySortOrderAscNameAsc().stream()
                .collect(Collectors.toMap(Attribute::getId, Attribute::getName));

        Map<UUID, List<AttributeValue>> byAttribute = new LinkedHashMap<>();
        valueById.values().stream()
                .sorted(Comparator.comparingInt(AttributeValue::getSortOrder).thenComparing(AttributeValue::getValue))
                .forEach(v -> byAttribute.computeIfAbsent(v.getAttributeId(), k -> new ArrayList<>()).add(v));
        List<List<AttributeValue>> axes = new ArrayList<>(byAttribute.values());

        List<List<AttributeValue>> combos = cartesian(axes);

        // Index existing variants by their attribute-value combination key.
        Map<UUID, List<VariantAttributeValue>> linksByVariant = variantAttributeValues
                .findByVariantIdIn(existing.stream().map(ProductVariant::getId).toList()).stream()
                .collect(Collectors.groupingBy(VariantAttributeValue::getVariantId));
        Map<String, ProductVariant> existingByKey = new LinkedHashMap<>();
        for (ProductVariant v : existing) {
            Set<UUID> ids = linksByVariant.getOrDefault(v.getId(), List.of()).stream()
                    .map(VariantAttributeValue::getAttributeValueId).collect(Collectors.toCollection(TreeSet::new));
            if (!ids.isEmpty()) existingByKey.put(comboKey(ids), v);
        }

        Set<UUID> keptIds = new TreeSet<>();
        for (List<AttributeValue> combo : combos) {
            Set<UUID> comboIds = combo.stream().map(AttributeValue::getId)
                    .collect(Collectors.toCollection(TreeSet::new));
            String key = comboKey(comboIds);
            String attrsJson = attributesJson(combo, attrNameById);
            ProductVariant v = existingByKey.get(key);
            if (v != null) {
                v.setActive(true);
                v.setDefaultVariant(false);
                v.setAttributes(attrsJson);
                keptIds.add(v.getId());
            } else {
                ProductVariant nv = ProductVariant.builder()
                        .productId(product.getId())
                        .sku(uniqueSku(product, combo))
                        .attributes(attrsJson)
                        .defaultVariant(false)
                        .active(true)
                        .build();
                variants.save(nv);
                for (AttributeValue av : combo) {
                    variantAttributeValues.save(VariantAttributeValue.builder()
                            .variantId(nv.getId()).attributeValueId(av.getId()).build());
                }
                keptIds.add(nv.getId());
            }
        }
        // Deactivate every variant that is no longer part of the matrix (incl. old default).
        for (ProductVariant v : existing) {
            if (!keptIds.contains(v.getId())) {
                v.setActive(false);
                v.setDefaultVariant(false);
            }
        }
    }

    private void regenerateDefaultOnly(Product product, List<ProductVariant> existing) {
        ProductVariant def = existing.stream().filter(ProductVariant::isDefaultVariant).findFirst()
                .orElse(null);
        if (def == null) {
            def = ProductVariant.builder()
                    .productId(product.getId())
                    .sku(uniqueDefaultSku(product))
                    .defaultVariant(true)
                    .active(true)
                    .build();
            variants.save(def);
        } else {
            def.setActive(true);
            def.setAttributes(null);
        }
        final UUID defId = def.getId();
        for (ProductVariant v : existing) {
            if (!v.getId().equals(defId)) {
                v.setActive(false);
                v.setDefaultVariant(false);
            }
        }
    }

    private static List<List<AttributeValue>> cartesian(List<List<AttributeValue>> axes) {
        List<List<AttributeValue>> result = new ArrayList<>();
        result.add(new ArrayList<>());
        for (List<AttributeValue> axis : axes) {
            List<List<AttributeValue>> next = new ArrayList<>();
            for (List<AttributeValue> prefix : result) {
                for (AttributeValue value : axis) {
                    List<AttributeValue> combo = new ArrayList<>(prefix);
                    combo.add(value);
                    next.add(combo);
                }
            }
            result = next;
        }
        return result;
    }

    private static String comboKey(Set<UUID> sortedIds) {
        return sortedIds.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    private String attributesJson(List<AttributeValue> combo, Map<UUID, String> attrNameById) {
        Map<String, String> map = new LinkedHashMap<>();
        for (AttributeValue v : combo) {
            map.put(attrNameById.getOrDefault(v.getAttributeId(), "?"), v.getValue());
        }
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialize variant attributes for product {}", combo, e);
            return null;
        }
    }

    private String uniqueSku(Product product, List<AttributeValue> combo) {
        String suffix = combo.stream()
                .map(v -> slug(v.getCode() != null && !v.getCode().isBlank() ? v.getCode() : v.getValue()))
                .collect(Collectors.joining("-"));
        return ensureUnique(product.getSku() + "-" + suffix);
    }

    private String uniqueDefaultSku(Product product) {
        return variants.existsBySku(product.getSku()) ? ensureUnique(product.getSku() + "-DEF")
                : product.getSku();
    }

    private String ensureUnique(String base) {
        String candidate = base;
        int n = 2;
        while (variants.existsBySku(candidate)) {
            candidate = base + "-" + n++;
        }
        return candidate;
    }

    private static String slug(String s) {
        return s.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "");
    }
}
