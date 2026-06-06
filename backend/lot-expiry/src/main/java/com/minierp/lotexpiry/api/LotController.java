package com.minierp.lotexpiry.api;

import com.minierp.lotexpiry.internal.LotService;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lots")
@RequiredArgsConstructor
@Tag(name = "Lot Expiry", description = "Product lot tracking, FEFO selection, expiry management")
public class LotController {

    private final LotService service;

    @GetMapping
    @PreAuthorize("hasAuthority('lot:read')")
    public PageResponse<LotDto.LotResponse> list(
            @RequestParam(required = false) UUID variantId,
            @RequestParam(required = false) UUID warehouseId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.listLots(variantId, warehouseId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('lot:read')")
    public LotDto.LotResponse get(@PathVariable UUID id) {
        return service.getLot(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('lot:create')")
    public LotDto.LotResponse create(@Valid @RequestBody LotDto.CreateLotRequest req) {
        return service.createLot(req.variantId(), req.warehouseId(), req.uomId(),
                req.lotNumber(), req.expirationDate(), req.productionDate(),
                req.quantity(), req.unitCost(), req.supplierId(), req.notes());
    }

    /** Opening stock for an expiry-tracked product: posts the stock-in AND creates its lot. */
    @PostMapping("/opening-balance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('stock:adjust')")
    public com.minierp.inventory.api.StockMovementDto openingBalance(
            @Valid @RequestBody LotDto.OpeningBalanceRequest req) {
        UUID userId = CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
        return service.openingBalanceWithLot(req.warehouseId(), req.variantId(), req.quantity(),
                req.unitCost(), req.lotNumber(), req.expirationDate(), req.productionDate(),
                req.notes(), userId);
    }

    @PostMapping("/{id}/block")
    @PreAuthorize("hasAuthority('lot:update')")
    public void block(@PathVariable UUID id, @RequestParam String reason) {
        service.blockLot(id, reason);
    }

    @PostMapping("/{id}/destroy")
    @PreAuthorize("hasAuthority('lot:delete')")
    public void destroy(@PathVariable UUID id, @Valid @RequestBody LotDto.DestroyLotRequest req) {
        UUID userId = CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
        service.destroyLot(id, req.quantity(), req.method(), req.cost(), req.notes(), userId);
    }

    /** CDC §15.4 — FEFO selection helper. */
    @PostMapping("/select-fefo")
    @PreAuthorize("hasAuthority('lot:read')")
    public List<LotAllocation> selectFefo(@Valid @RequestBody LotDto.SelectFefoRequest req) {
        return service.selectFEFO(req.variantId(), req.warehouseId(), req.quantity());
    }

    /** CDC §15.4 — lots expiring within N days. */
    @GetMapping("/expiring")
    @PreAuthorize("hasAuthority('lot:read')")
    public PageResponse<LotDto.LotResponse> expiring(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) UUID warehouseId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.listExpiringWithin(days, warehouseId, pageable));
    }

    /** CDC §15.4 — expired lots awaiting destruction. */
    @GetMapping("/expired")
    @PreAuthorize("hasAuthority('lot:read')")
    public PageResponse<LotDto.LotResponse> expired(
            @RequestParam(required = false) UUID warehouseId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.listExpired(warehouseId, pageable));
    }

    @GetMapping("/alert-configs")
    @PreAuthorize("hasAuthority('lot:read')")
    public List<LotDto.AlertConfigResponse> listAlertConfigs() {
        return service.listAlertConfigs();
    }

    @PostMapping("/alert-configs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('lot:create')")
    public LotDto.AlertConfigResponse createAlertConfig(@Valid @RequestBody LotDto.AlertConfigRequest req) {
        return service.saveAlertConfig(null, req.daysBeforeExpiry(),
                req.severity(), req.notifyRoles(), req.enabled());
    }

    @PutMapping("/alert-configs/{id}")
    @PreAuthorize("hasAuthority('lot:update')")
    public LotDto.AlertConfigResponse updateAlertConfig(
            @PathVariable UUID id,
            @Valid @RequestBody LotDto.AlertConfigRequest req) {
        return service.saveAlertConfig(id, req.daysBeforeExpiry(),
                req.severity(), req.notifyRoles(), req.enabled());
    }

    /** CDC §4.1 — product label PDF (50×30mm) with expiration date. */
    @GetMapping("/{id}/label.pdf")
    @PreAuthorize("hasAuthority('lot:read')")
    public org.springframework.http.ResponseEntity<byte[]> label(@PathVariable UUID id) {
        byte[] pdf = service.generateLabelPdf(id);
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"lot-label-" + id + ".pdf\"")
                .body(pdf);
    }
}
