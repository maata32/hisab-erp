package com.minierp.uom.api;

import com.minierp.uom.internal.UomService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uoms")
@RequiredArgsConstructor
@Tag(name = "UoM", description = "Unit-of-measure categories and units")
public class UomController {

    private final UomService service;
    private final UomLookup lookup;

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('uom:read')")
    public List<UomCategoryDto> categories() {
        return service.listCategories();
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('uom:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public UomCategoryDto createCategory(@Valid @RequestBody CreateUomCategoryRequest req) {
        return service.createCategory(req.code(), req.name());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('uom:read')")
    public List<UomDto> all() {
        return service.listAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('uom:create')")
    @ResponseStatus(HttpStatus.CREATED)
    public UomDto createUom(@Valid @RequestBody CreateUomRequest req) {
        return service.createUom(req.categoryId(), req.code(), req.name(),
                req.ratioToBase(), req.isBase() != null && req.isBase(),
                req.decimalPlaces() == null ? 3 : req.decimalPlaces());
    }

    @GetMapping("/convert")
    @PreAuthorize("hasAuthority('uom:read')")
    public ConversionResult convert(
            @RequestParam BigDecimal amount,
            @RequestParam UUID from,
            @RequestParam UUID to) {
        BigDecimal result = lookup.convert(amount, from, to);
        return new ConversionResult(amount, from, to, result);
    }

    public record CreateUomCategoryRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 100) String name) {}

    public record CreateUomRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 100) String name,
            @NotNull @DecimalMin("0.000001") BigDecimal ratioToBase,
            Boolean isBase,
            @Min(0) @Max(6) Integer decimalPlaces) {}

    public record ConversionResult(BigDecimal amount, UUID fromUomId, UUID toUomId, BigDecimal result) {}
}
