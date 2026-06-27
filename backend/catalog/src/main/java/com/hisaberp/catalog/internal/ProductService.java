package com.hisaberp.catalog.internal;

import com.hisaberp.catalog.api.ProductDto;
import com.hisaberp.catalog.api.ProductDto.ProductImageDto;
import com.hisaberp.catalog.api.ProductDto.ProductPackagingDto;
import com.hisaberp.catalog.api.ProductDto.ProductVariantDto;
import com.hisaberp.catalog.events.ProductCreatedEvent;
import com.hisaberp.shared.error.BusinessException;
import com.hisaberp.shared.error.ConflictException;
import com.hisaberp.shared.error.NotFoundException;
import com.hisaberp.shared.persistence.TenantGuard;
import com.hisaberp.shared.tenant.TenantContext;
import com.hisaberp.shared.util.PageResponse;
import com.hisaberp.tenant.api.PlanLimits;
import com.hisaberp.tenant.api.TenantLookup;
import com.hisaberp.uom.api.UomDto;
import com.hisaberp.uom.api.UomLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository products;
    private final ProductPackagingRepository packagings;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final ProductAttributeValueRepository productAttributeValues;
    private final VariantAttributeValueRepository variantAttributeValues;
    private final VariantGenerationService variantGeneration;
    private final BrandRepository brands;
    private final ProductCategoryRepository categories;
    private final UomLookup uomLookup;
    private final ApplicationEventPublisher events;
    private final ProductImageStorageService imageStorage;
    private final TenantLookup tenantLookup;

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> search(String q, UUID categoryId, UUID brandId,
                                            boolean includeInactive, Pageable pageable) {
        return PageResponse.of(products.search(q, categoryId, brandId, includeInactive, pageable)
                .map(this::toDto));
    }

    @Transactional(readOnly = true)
    public ProductDto get(UUID id) {
        return toDto(loadInTenant(id));
    }

    /**
     * Load a product by id, enforcing it belongs to the current tenant. {@code findById}
     * bypasses the Hibernate tenant filter, so without this guard a token from tenant A could
     * read/modify a product of tenant B (BUG-2 / SEC-02).
     */
    private Product loadInTenant(UUID id) {
        return TenantGuard.requireSameTenant(products.findById(id),
                () -> NotFoundException.of("entity.product", id));
    }

    @Transactional
    public ProductDto create(CreateProductRequest req) {
        if (products.existsBySku(req.sku())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "sku", "value", req.sku()));
        }
        if (req.barcode() != null && !req.barcode().isBlank() && products.existsByBarcode(req.barcode())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "barcode", "value", req.barcode()));
        }
        if (req.categoryId() != null && categories.findById(req.categoryId()).isEmpty()) {
            throw NotFoundException.of("entity.product_category", req.categoryId());
        }
        if (req.brandId() != null && brands.findById(req.brandId()).isEmpty()) {
            throw NotFoundException.of("entity.brand", req.brandId());
        }
        UomDto baseUom = uomLookup.findById(req.baseUomId())
                .orElseThrow(() -> NotFoundException.of("entity.uom", req.baseUomId()));

        Product p = Product.builder()
                .sku(req.sku())
                .barcode(blankToNull(req.barcode()))
                .name(req.name())
                .description(req.description())
                .categoryId(req.categoryId())
                .brandId(req.brandId())
                .baseUomId(baseUom.id())
                .defaultTaxRate(req.defaultTaxRate() == null ? new BigDecimal("0.16") : req.defaultTaxRate())
                .tracksLots(Boolean.TRUE.equals(req.tracksLots()))
                .trackExpiry(Boolean.TRUE.equals(req.trackExpiry()))
                .shelfLifeDays(req.shelfLifeDays())
                .tracksSerial(Boolean.TRUE.equals(req.tracksSerial()))
                .sellable(req.sellable() == null || req.sellable())
                .purchasable(req.purchasable() == null || req.purchasable())
                .uniformPricing(req.uniformPricing() == null || req.uniformPricing())
                .imageUrl(req.imageUrl())
                .weightGrams(req.weightGrams())
                .build();
        products.save(p);

        if (req.packagings() != null) {
            for (CreatePackagingRequest cp : req.packagings()) {
                addPackaging(p, cp);
            }
        }

        // Every product is backed by at least one variant (the real SKU). When attribute
        // values are supplied we generate the matrix, otherwise a single default variant.
        if (req.attributeValueIds() != null && !req.attributeValueIds().isEmpty()) {
            variantGeneration.setAttributeValues(p.getId(), req.attributeValueIds());
        } else {
            variantGeneration.ensureDefaultVariant(p);
        }

        events.publishEvent(new ProductCreatedEvent(
                p.getTenantId(), p.getId(), p.getSku(), p.getName(), Instant.now()));
        return toDto(p);
    }

    @Transactional
    public ProductDto update(UUID id, UpdateProductRequest req) {
        Product p = loadInTenant(id);
        if (req.barcode() != null && !req.barcode().equals(p.getBarcode())
                && !req.barcode().isBlank() && products.existsByBarcode(req.barcode())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "barcode", "value", req.barcode()));
        }
        if (req.name() != null) p.setName(req.name());
        if (req.description() != null) p.setDescription(req.description());
        if (req.categoryId() != null) p.setCategoryId(req.categoryId());
        if (req.brandId() != null) p.setBrandId(req.brandId());
        if (req.barcode() != null) p.setBarcode(blankToNull(req.barcode()));
        if (req.baseUomId() != null) {
            uomLookup.findById(req.baseUomId())
                    .orElseThrow(() -> NotFoundException.of("entity.uom", req.baseUomId()));
            p.setBaseUomId(req.baseUomId());
        }
        if (req.defaultTaxRate() != null) p.setDefaultTaxRate(req.defaultTaxRate());
        if (req.uniformPricing() != null) p.setUniformPricing(req.uniformPricing());
        if (req.trackExpiry() != null) p.setTrackExpiry(req.trackExpiry());
        if (req.shelfLifeDays() != null) p.setShelfLifeDays(req.shelfLifeDays());
        if (req.sellable() != null) p.setSellable(req.sellable());
        if (req.purchasable() != null) p.setPurchasable(req.purchasable());
        if (req.active() != null) p.setActive(req.active());
        if (req.imageUrl() != null) p.setImageUrl(req.imageUrl());
        if (req.weightGrams() != null) p.setWeightGrams(req.weightGrams());
        return toDto(p);
    }

    @Transactional
    public ProductDto.ProductPackagingDto addPackaging(UUID productId, CreatePackagingRequest req) {
        Product p = loadInTenant(productId);
        return addPackaging(p, req);
    }

    private ProductDto.ProductPackagingDto addPackaging(Product p, CreatePackagingRequest req) {
        UomDto uom = uomLookup.findById(req.uomId())
                .orElseThrow(() -> NotFoundException.of("entity.uom", req.uomId()));
        UomDto base = uomLookup.findById(p.getBaseUomId())
                .orElseThrow(() -> NotFoundException.of("entity.uom", p.getBaseUomId()));
        if (!uom.categoryId().equals(base.categoryId())) {
            throw new BusinessException("error.uom.category_mismatch",
                    Map.of("from", uom.code(), "to", base.code()));
        }
        if (req.factor() == null || req.factor().signum() <= 0) {
            throw new BusinessException("error.uom.invalid_ratio", Map.of());
        }
        ProductPackaging pp = ProductPackaging.builder()
                .productId(p.getId())
                .uomId(req.uomId())
                .factor(req.factor())
                .barcode(blankToNull(req.barcode()))
                .defaultSale(Boolean.TRUE.equals(req.defaultSale()))
                .defaultPurchase(Boolean.TRUE.equals(req.defaultPurchase()))
                .stockUom(Boolean.TRUE.equals(req.stockUom()))
                .sortOrder(req.sortOrder() == null ? 0 : req.sortOrder())
                .active(req.active() == null || req.active())
                .build();
        packagings.save(pp);
        return new ProductPackagingDto(pp.getId(), pp.getUomId(), pp.getFactor(),
                pp.getBarcode(), pp.isDefaultSale(), pp.isDefaultPurchase(),
                pp.isStockUom(), pp.getSortOrder(), pp.isActive());
    }

    @Transactional(readOnly = true)
    public List<ProductPackagingDto> listUomsForProduct(UUID productId) {
        return packagings.findByProductId(productId).stream()
                .sorted(java.util.Comparator.comparingInt(ProductPackaging::getSortOrder))
                .map(pp -> new ProductPackagingDto(pp.getId(), pp.getUomId(), pp.getFactor(),
                        pp.getBarcode(), pp.isDefaultSale(), pp.isDefaultPurchase(),
                        pp.isStockUom(), pp.getSortOrder(), pp.isActive()))
                .toList();
    }

    @Transactional
    public void removePackaging(UUID productId, UUID packagingId) {
        loadInTenant(productId);
        ProductPackaging pp = packagings.findById(packagingId)
                .orElseThrow(() -> NotFoundException.of("entity.product_packaging", packagingId));
        if (!pp.getProductId().equals(productId)) {
            throw NotFoundException.of("entity.product_packaging", packagingId);
        }
        packagings.delete(pp);
    }

    /** Set the product's enabled attribute values and regenerate its variant matrix. */
    @Transactional
    public ProductDto setAttributeValues(UUID productId, List<UUID> attributeValueIds) {
        loadInTenant(productId);
        variantGeneration.setAttributeValues(productId, attributeValueIds);
        return get(productId);
    }

    /** Edit a single generated variant's SKU / barcode / active flag. */
    @Transactional
    public ProductVariantDto updateVariant(UUID productId, UUID variantId, UpdateVariantRequest req) {
        loadInTenant(productId);
        ProductVariant v = variants.findById(variantId)
                .orElseThrow(() -> NotFoundException.of("entity.product_variant", variantId));
        if (!v.getProductId().equals(productId)) throw NotFoundException.of("entity.product_variant", variantId);
        if (req.sku() != null && !req.sku().isBlank() && !req.sku().equals(v.getSku())) {
            if (variants.existsBySku(req.sku())) {
                throw new ConflictException("error.data_integrity", Map.of("field", "sku", "value", req.sku()));
            }
            v.setSku(req.sku());
        }
        if (req.barcode() != null) v.setBarcode(blankToNull(req.barcode()));
        if (req.active() != null) v.setActive(req.active());
        return toVariantDto(v);
    }

    @Transactional
    public ProductImageDto addImage(UUID productId, CreateImageRequest req) {
        loadInTenant(productId);
        ProductImage img = ProductImage.builder()
                .productId(productId)
                .url(req.url())
                .position(req.position() == null ? 0 : req.position())
                .altText(req.altText())
                .build();
        images.save(img);
        return toImageDto(img);
    }

    @Transactional
    public ProductImageDto uploadImage(UUID productId, MultipartFile file, Integer position, String altText) {
        loadInTenant(productId);
        enforceImageQuota(productId);
        String url = imageStorage.upload(file);
        ProductImage img = ProductImage.builder()
                .productId(productId)
                .url(url)
                .position(position == null ? 0 : position)
                .altText(altText)
                .build();
        images.save(img);
        return toImageDto(img);
    }

    private void enforceImageQuota(UUID productId) {
        PlanLimits limits = tenantLookup.findLimitsForTenant(TenantContext.require());
        Integer max = limits.maxProductImages();
        if (max == null) return; // unlimited
        long current = images.countByProductId(productId);
        if (current >= max) {
            throw new BusinessException("error.product_image.quota_exceeded",
                    Map.of("max", max, "current", current));
        }
    }

    @Transactional
    public void removeImage(UUID productId, UUID imageId) {
        loadInTenant(productId);
        ProductImage img = images.findById(imageId)
                .orElseThrow(() -> NotFoundException.of("entity.product_image", imageId));
        if (!img.getProductId().equals(productId)) throw NotFoundException.of("entity.product_image", imageId);
        images.delete(img);
    }

    private ProductDto toDto(Product p) {
        var pkgs = packagings.findByProductId(p.getId()).stream()
                .map(pp -> new ProductPackagingDto(pp.getId(), pp.getUomId(), pp.getFactor(),
                        pp.getBarcode(), pp.isDefaultSale(), pp.isDefaultPurchase(),
                        pp.isStockUom(), pp.getSortOrder(), pp.isActive()))
                .toList();
        var variantDtos = variants.findByProductIdOrderBySkuAsc(p.getId()).stream()
                .map(this::toVariantDto).toList();
        var imageDtos = images.findByProductIdOrderByPositionAsc(p.getId()).stream()
                .map(this::toImageDto).toList();
        var attributeValueIds = productAttributeValues.findByProductId(p.getId()).stream()
                .map(ProductAttributeValue::getAttributeValueId).toList();
        return new ProductDto(p.getId(), p.getSku(), p.getBarcode(), p.getName(),
                p.getDescription(), p.getCategoryId(), p.getBrandId(), p.getBaseUomId(),
                p.getDefaultTaxRate(), p.isTracksLots(), p.isTrackExpiry(), p.getShelfLifeDays(),
                p.isTracksSerial(), p.isSellable(), p.isPurchasable(), p.isActive(),
                p.isUniformPricing(), p.getImageUrl(), p.getWeightGrams(), attributeValueIds,
                pkgs, variantDtos, imageDtos);
    }

    private ProductVariantDto toVariantDto(ProductVariant v) {
        var valueIds = variantAttributeValues.findByVariantId(v.getId()).stream()
                .map(VariantAttributeValue::getAttributeValueId).toList();
        return new ProductVariantDto(v.getId(), v.getSku(), v.getBarcode(), v.getAttributes(),
                valueIds, v.isDefaultVariant(), v.isActive());
    }

    private ProductImageDto toImageDto(ProductImage img) {
        return new ProductImageDto(img.getId(), img.getUrl(), img.getPosition(), img.getAltText());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public record CreateProductRequest(
            String sku,
            String barcode,
            String name,
            String description,
            UUID categoryId,
            UUID brandId,
            UUID baseUomId,
            BigDecimal defaultTaxRate,
            Boolean tracksLots,
            Boolean trackExpiry,
            Integer shelfLifeDays,
            Boolean tracksSerial,
            Boolean sellable,
            Boolean purchasable,
            Boolean uniformPricing,
            String imageUrl,
            BigDecimal weightGrams,
            List<UUID> attributeValueIds,
            List<CreatePackagingRequest> packagings) {}

    public record UpdateProductRequest(
            String name,
            String description,
            String barcode,
            UUID categoryId,
            UUID brandId,
            UUID baseUomId,
            BigDecimal defaultTaxRate,
            Boolean uniformPricing,
            Boolean trackExpiry,
            Integer shelfLifeDays,
            Boolean sellable,
            Boolean purchasable,
            Boolean active,
            String imageUrl,
            BigDecimal weightGrams) {}

    public record CreatePackagingRequest(
            UUID uomId,
            BigDecimal factor,
            String barcode,
            Boolean defaultSale,
            Boolean defaultPurchase,
            Boolean stockUom,
            Integer sortOrder,
            Boolean active) {}

    public record SetAttributeValuesRequest(
            List<UUID> attributeValueIds) {}

    public record UpdateVariantRequest(
            String sku,
            String barcode,
            Boolean active) {}

    public record CreateImageRequest(
            String url,
            Integer position,
            String altText) {}
}
