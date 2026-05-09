package com.minierp.catalog.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductDto(
        UUID id,
        String sku,
        String barcode,
        String name,
        String description,
        UUID categoryId,
        UUID brandId,
        UUID baseUomId,
        BigDecimal defaultTaxRate,
        boolean tracksLots,
        boolean tracksSerial,
        boolean sellable,
        boolean purchasable,
        boolean active,
        String imageUrl,
        BigDecimal weightGrams,
        List<ProductPackagingDto> packagings,
        List<ProductVariantDto> variants,
        List<ProductImageDto> images) {

    public record ProductPackagingDto(
            UUID id,
            UUID uomId,
            BigDecimal factor,
            String barcode,
            boolean defaultSale,
            boolean defaultPurchase) {}

    public record ProductVariantDto(
            UUID id,
            String sku,
            String barcode,
            String attributes,
            boolean active) {}

    public record ProductImageDto(
            UUID id,
            String url,
            int position,
            String altText) {}
}
