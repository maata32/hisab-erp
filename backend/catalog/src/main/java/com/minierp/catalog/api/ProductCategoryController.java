package com.minierp.catalog.api;

import com.minierp.catalog.internal.ProductCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/product-categories")
@RequiredArgsConstructor
@Tag(name = "Product categories")
public class ProductCategoryController {

    private final ProductCategoryService service;

    @GetMapping
    @PreAuthorize("hasAuthority('product:read')")
    public List<ProductCategoryDto> tree() {
        return service.tree();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductCategoryDto create(@Valid @RequestBody CreateCategoryRequest req) {
        return service.create(req.code(), req.name(), req.parentId(), req.sortOrder());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('product:update')")
    public ProductCategoryDto update(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest req) {
        return service.update(id, req.name(), req.sortOrder(), req.active());
    }

    public record CreateCategoryRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            UUID parentId,
            Integer sortOrder) {}

    public record UpdateCategoryRequest(
            @Size(max = 200) String name,
            Integer sortOrder,
            Boolean active) {}
}
