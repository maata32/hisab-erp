package com.minierp.catalog.internal;

import com.minierp.catalog.api.ProductDto;
import com.minierp.catalog.api.ProductDto.ProductImageDto;
import com.minierp.catalog.api.ProductDto.ProductPackagingDto;
import com.minierp.catalog.api.ProductDto.ProductVariantDto;
import com.minierp.catalog.events.ProductCreatedEvent;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.util.PageResponse;
import com.minierp.uom.api.UomDto;
import com.minierp.uom.api.UomLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final BrandRepository brands;
    private final ProductCategoryRepository categories;
    private final UomLookup uomLookup;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<ProductDto> search(String q, UUID categoryId, UUID brandId, Pageable pageable) {
        return PageResponse.of(products.search(q, categoryId, brandId, pageable).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public ProductDto get(UUID id) {
        return toDto(products.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.product", id)));
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
                .imageUrl(req.imageUrl())
                .weightGrams(req.weightGrams())
                .build();
        products.save(p);

        if (req.packagings() != null) {
            for (CreatePackagingRequest cp : req.packagings()) {
                addPackaging(p, cp);
            }
        }

        events.publishEvent(new ProductCreatedEvent(
                p.getTenantId(), p.getId(), p.getSku(), p.getName(), Instant.now()));
        return toDto(p);
    }

    @Transactional
    public ProductDto update(UUID id, UpdateProductRequest req) {
        Product p = products.findById(id).orElseThrow(() -> NotFoundException.of("entity.product", id));
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
        if (req.defaultTaxRate() != null) p.setDefaultTaxRate(req.defaultTaxRate());
        if (req.sellable() != null) p.setSellable(req.sellable());
        if (req.purchasable() != null) p.setPurchasable(req.purchasable());
        if (req.active() != null) p.setActive(req.active());
        if (req.imageUrl() != null) p.setImageUrl(req.imageUrl());
        if (req.weightGrams() != null) p.setWeightGrams(req.weightGrams());
        return toDto(p);
    }

    @Transactional
    public ProductDto.ProductPackagingDto addPackaging(UUID productId, CreatePackagingRequest req) {
        Product p = products.findById(productId)
                .orElseThrow(() -> NotFoundException.of("entity.product", productId));
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
                .build();
        packagings.save(pp);
        return new ProductPackagingDto(pp.getId(), pp.getUomId(), pp.getFactor(),
                pp.getBarcode(), pp.isDefaultSale(), pp.isDefaultPurchase());
    }

    @Transactional
    public void removePackaging(UUID productId, UUID packagingId) {
        ProductPackaging pp = packagings.findById(packagingId)
                .orElseThrow(() -> NotFoundException.of("entity.product_packaging", packagingId));
        if (!pp.getProductId().equals(productId)) {
            throw NotFoundException.of("entity.product_packaging", packagingId);
        }
        packagings.delete(pp);
    }

    @Transactional
    public ProductVariantDto addVariant(UUID productId, CreateVariantRequest req) {
        products.findById(productId).orElseThrow(() -> NotFoundException.of("entity.product", productId));
        ProductVariant v = ProductVariant.builder()
                .productId(productId)
                .sku(blankToNull(req.sku()))
                .barcode(blankToNull(req.barcode()))
                .attributes(req.attributes())
                .build();
        variants.save(v);
        return toVariantDto(v);
    }

    @Transactional
    public void removeVariant(UUID productId, UUID variantId) {
        ProductVariant v = variants.findById(variantId)
                .orElseThrow(() -> NotFoundException.of("entity.product_variant", variantId));
        if (!v.getProductId().equals(productId)) throw NotFoundException.of("entity.product_variant", variantId);
        variants.delete(v);
    }

    @Transactional
    public ProductImageDto addImage(UUID productId, CreateImageRequest req) {
        products.findById(productId).orElseThrow(() -> NotFoundException.of("entity.product", productId));
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
    public void removeImage(UUID productId, UUID imageId) {
        ProductImage img = images.findById(imageId)
                .orElseThrow(() -> NotFoundException.of("entity.product_image", imageId));
        if (!img.getProductId().equals(productId)) throw NotFoundException.of("entity.product_image", imageId);
        images.delete(img);
    }

    private ProductDto toDto(Product p) {
        var pkgs = packagings.findByProductId(p.getId()).stream()
                .map(pp -> new ProductPackagingDto(pp.getId(), pp.getUomId(), pp.getFactor(),
                        pp.getBarcode(), pp.isDefaultSale(), pp.isDefaultPurchase()))
                .toList();
        var variantDtos = variants.findByProductIdOrderBySkuAsc(p.getId()).stream()
                .map(this::toVariantDto).toList();
        var imageDtos = images.findByProductIdOrderByPositionAsc(p.getId()).stream()
                .map(this::toImageDto).toList();
        return new ProductDto(p.getId(), p.getSku(), p.getBarcode(), p.getName(),
                p.getDescription(), p.getCategoryId(), p.getBrandId(), p.getBaseUomId(),
                p.getDefaultTaxRate(), p.isTracksLots(), p.isTrackExpiry(), p.getShelfLifeDays(),
                p.isTracksSerial(), p.isSellable(), p.isPurchasable(), p.isActive(),
                p.getImageUrl(), p.getWeightGrams(), pkgs, variantDtos, imageDtos);
    }

    private ProductVariantDto toVariantDto(ProductVariant v) {
        return new ProductVariantDto(v.getId(), v.getSku(), v.getBarcode(), v.getAttributes(), v.isActive());
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
            String imageUrl,
            BigDecimal weightGrams,
            List<CreatePackagingRequest> packagings) {}

    public record UpdateProductRequest(
            String name,
            String description,
            String barcode,
            UUID categoryId,
            UUID brandId,
            BigDecimal defaultTaxRate,
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
            Boolean defaultPurchase) {}

    public record CreateVariantRequest(
            String sku,
            String barcode,
            String attributes) {}

    public record CreateImageRequest(
            String url,
            Integer position,
            String altText) {}
}
