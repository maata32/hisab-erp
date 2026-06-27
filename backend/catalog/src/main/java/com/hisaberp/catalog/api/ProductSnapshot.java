package com.hisaberp.catalog.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductSnapshot(
        UUID id,
        String sku,
        String barcode,
        String name,
        UUID categoryId,
        UUID brandId,
        UUID baseUomId,
        BigDecimal defaultTaxRate,
        boolean tracksLots,
        boolean trackExpiry,
        Integer shelfLifeDays,
        boolean sellable,
        boolean active,
        List<PackagingSnapshot> packagings) {

    public record PackagingSnapshot(UUID id, UUID uomId, BigDecimal factor, String barcode,
                                    boolean defaultSale, boolean defaultPurchase) {}
}
