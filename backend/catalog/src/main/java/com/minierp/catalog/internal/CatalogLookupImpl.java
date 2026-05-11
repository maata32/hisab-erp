package com.minierp.catalog.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.catalog.api.ProductSnapshot;
import com.minierp.catalog.api.ProductSnapshot.PackagingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class CatalogLookupImpl implements CatalogLookup {

    private final ProductRepository products;
    private final ProductPackagingRepository packagings;

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductSnapshot> findProductById(UUID id) {
        return products.findById(id).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductSnapshot> findProductBySku(String sku) {
        return products.findBySku(sku).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductSnapshot> findProductByBarcode(String barcode) {
        return products.findByBarcode(barcode).map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductSnapshot> findProductsByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return products.findAllByIdIn(ids).stream().map(this::toSnapshot).toList();
    }

    private ProductSnapshot toSnapshot(Product p) {
        var pkgs = packagings.findByProductId(p.getId()).stream()
                .map(pp -> new PackagingSnapshot(pp.getId(), pp.getUomId(), pp.getFactor(),
                        pp.getBarcode(), pp.isDefaultSale(), pp.isDefaultPurchase()))
                .toList();
        return new ProductSnapshot(p.getId(), p.getSku(), p.getBarcode(), p.getName(),
                p.getCategoryId(), p.getBrandId(), p.getBaseUomId(),
                p.getDefaultTaxRate(), p.isTracksLots(), p.isTrackExpiry(), p.getShelfLifeDays(),
                p.isSellable(), p.isActive(), pkgs);
    }
}
