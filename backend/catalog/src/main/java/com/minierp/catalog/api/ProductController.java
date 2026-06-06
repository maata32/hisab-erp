package com.minierp.catalog.api;

import com.minierp.catalog.internal.ProductService;
import com.minierp.catalog.internal.ProductService.CreateImageRequest;
import com.minierp.catalog.internal.ProductService.CreatePackagingRequest;
import com.minierp.catalog.internal.ProductService.CreateProductRequest;
import com.minierp.catalog.internal.ProductService.SetAttributeValuesRequest;
import com.minierp.catalog.internal.ProductService.UpdateProductRequest;
import com.minierp.catalog.internal.ProductService.UpdateVariantRequest;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
            @RequestParam(defaultValue = "false") boolean includeInactive,
            Pageable pageable) {
        return service.search(q, categoryId, brandId, includeInactive, pageable);
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

    /** CDC §15.4 alias — packagings exposed as UoMs available for the product. */
    @GetMapping("/{id}/uoms")
    @PreAuthorize("hasAuthority('product:read')")
    public java.util.List<ProductDto.ProductPackagingDto> listUoms(@PathVariable UUID id) {
        return service.listUomsForProduct(id);
    }

    @PostMapping("/{id}/uoms")
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto.ProductPackagingDto addUom(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePackagingRequest req) {
        return service.addPackaging(id, req);
    }

    /** Set the product's enabled attribute values and regenerate its variant matrix. */
    @PutMapping("/{id}/attributes")
    @PreAuthorize("hasAuthority('product:update')")
    public ProductDto setAttributeValues(
            @PathVariable UUID id,
            @RequestBody SetAttributeValuesRequest req) {
        return service.setAttributeValues(id, req.attributeValueIds());
    }

    @PatchMapping("/{id}/variants/{variantId}")
    @PreAuthorize("hasAuthority('product:update')")
    public ProductDto.ProductVariantDto updateVariant(
            @PathVariable UUID id,
            @PathVariable UUID variantId,
            @RequestBody UpdateVariantRequest req) {
        return service.updateVariant(id, variantId, req);
    }

    @PostMapping("/{id}/images")
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto.ProductImageDto addImage(
            @PathVariable UUID id,
            @Valid @RequestBody CreateImageRequest req) {
        return service.addImage(id, req);
    }

    @PostMapping(value = "/{id}/images/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto.ProductImageDto uploadImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "position", required = false) Integer position,
            @RequestParam(value = "altText", required = false) String altText) {
        return service.uploadImage(id, file, position, altText);
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasAuthority('product:update')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        service.removeImage(id, imageId);
    }
}
