package com.minierp.catalog.api;

import com.minierp.catalog.internal.ProductService;
import com.minierp.catalog.internal.ProductService.CreateImageRequest;
import com.minierp.catalog.internal.ProductService.CreatePackagingRequest;
import com.minierp.catalog.internal.ProductService.CreateProductRequest;
import com.minierp.catalog.internal.ProductService.CreateVariantRequest;
import com.minierp.catalog.internal.ProductService.UpdateProductRequest;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products")
public class ProductController {

    private final ProductService service;

    @GetMapping
    @PreAuthorize("hasAuthority('product:read')")
    public PageResponse<ProductDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            Pageable pageable) {
        return service.search(q, categoryId, brandId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:read')")
    public ProductDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto create(@Valid @RequestBody CreateProductRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('product:update')")
    public ProductDto update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/packagings")
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto.ProductPackagingDto addPackaging(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePackagingRequest req) {
        return service.addPackaging(id, req);
    }

    @DeleteMapping("/{id}/packagings/{packagingId}")
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removePackaging(@PathVariable UUID id, @PathVariable UUID packagingId) {
        service.removePackaging(id, packagingId);
    }

    @PostMapping("/{id}/variants")
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto.ProductVariantDto addVariant(
            @PathVariable UUID id,
            @Valid @RequestBody CreateVariantRequest req) {
        return service.addVariant(id, req);
    }

    @DeleteMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeVariant(@PathVariable UUID id, @PathVariable UUID variantId) {
        service.removeVariant(id, variantId);
    }

    @PostMapping("/{id}/images")
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto.ProductImageDto addImage(
            @PathVariable UUID id,
            @Valid @RequestBody CreateImageRequest req) {
        return service.addImage(id, req);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        service.removeImage(id, imageId);
    }
}
