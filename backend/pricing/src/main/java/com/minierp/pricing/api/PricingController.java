package com.minierp.pricing.api;

import com.minierp.pricing.internal.PricingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing", description = "Price tiers, product prices and resolver")
public class PricingController {

    private final PricingService service;
    private final PriceResolver resolver;

    @GetMapping("/tiers")
    @PreAuthorize("hasAuthority('product:read')")
    public List<PriceTierDto> listTiers() {
        return service.listTiers();
    }

    @PostMapping("/tiers")
    @PreAuthorize("hasAuthority('price:update')")
    @ResponseStatus(HttpStatus.CREATED)
    public PriceTierDto createTier(@Valid @RequestBody CreateTierRequest req) {
        return service.createTier(req.code(), req.name(), req.defaultTier());
    }

    @GetMapping("/products/{productId}")
    @PreAuthorize("hasAuthority('product:read')")
    public List<ProductPriceDto> listForProduct(@PathVariable UUID productId) {
        return service.listForProduct(productId);
    }

    @PutMapping("/products/{productId}")
    @PreAuthorize("hasAuthority('price:update')")
    public ProductPriceDto upsert(@PathVariable UUID productId,
                                  @Valid @RequestBody UpsertPriceRequest req) {
        return service.upsertPrice(productId, req.uomId(), req.priceTierId(),
                req.amount(), req.currency(), req.taxInclusive(),
                req.validFrom(), req.validTo(), req.minQty());
    }

    @GetMapping("/resolve")
    @PreAuthorize("hasAuthority('product:read')")
    public ResolvedPrice resolve(
            @RequestParam UUID productId,
            @RequestParam(required = false) UUID uomId,
            @RequestParam(required = false) UUID priceTierId,
            @RequestParam BigDecimal quantity,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) BigDecimal unitDiscount) {
        return resolver.resolve(productId, uomId, priceTierId, quantity, date, unitDiscount);
    }

    public record CreateTierRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            Boolean defaultTier) {}

    public record UpsertPriceRequest(
            @NotNull UUID uomId,
            @NotNull UUID priceTierId,
            @NotNull @DecimalMin("0.00") BigDecimal amount,
            @Size(min = 3, max = 3) String currency,
            Boolean taxInclusive,
            LocalDate validFrom,
            LocalDate validTo,
            @DecimalMin("0.000001") BigDecimal minQty) {}
}
