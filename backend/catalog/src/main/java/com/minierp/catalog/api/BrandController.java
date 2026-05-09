package com.minierp.catalog.api;

import com.minierp.catalog.internal.BrandService;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
@Tag(name = "Brands", description = "Product brands")
public class BrandController {

    private final BrandService service;

    @GetMapping
    @PreAuthorize("hasAuthority('product:read')")
    public PageResponse<BrandDto> list(Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:read')")
    public BrandDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public BrandDto create(@Valid @RequestBody CreateBrandRequest req) {
        return service.create(req.code(), req.name(), req.description(), req.logoUrl());
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('product:update')")
    public BrandDto update(@PathVariable UUID id, @Valid @RequestBody UpdateBrandRequest req) {
        return service.update(id, req.name(), req.description(), req.logoUrl(), req.active());
    }

    public record CreateBrandRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Size(max = 500) String logoUrl) {}

    public record UpdateBrandRequest(
            @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @Size(max = 500) String logoUrl,
            Boolean active) {}
}
