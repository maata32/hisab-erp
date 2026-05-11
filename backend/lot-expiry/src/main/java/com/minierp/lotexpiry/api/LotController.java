package com.minierp.lotexpiry.api;

import com.minierp.lotexpiry.internal.LotService;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID warehouseId,
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.listLots(productId, warehouseId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('lot:read')")
    public LotDto.LotResponse get(@PathVariable UUID id) {
        return service.getLot(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('lot:write')")
    public LotDto.LotResponse create(@Valid @RequestBody LotDto.CreateLotRequest req) {
        return service.createLot(req.productId(), req.warehouseId(), req.uomId(),
                req.lotNumber(), req.expirationDate(), req.productionDate(),
                req.quantity(), req.unitCost(), req.supplierId(), req.notes());
    }

    @PostMapping("/{id}/block")
    @PreAuthorize("hasAuthority('lot:write')")
    public void block(@PathVariable UUID id, @RequestParam String reason) {
        service.blockLot(id, reason);
    }

    @PostMapping("/{id}/destroy")
    @PreAuthorize("hasAuthority('lot:write')")
    public void destroy(@PathVariable UUID id, @Valid @RequestBody LotDto.DestroyLotRequest req) {
        UUID userId = CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
        service.destroyLot(id, req.quantity(), req.method(), req.cost(), req.notes(), userId);
    }

    @GetMapping("/alert-configs")
    @PreAuthorize("hasAuthority('lot:read')")
    public List<LotDto.AlertConfigResponse> listAlertConfigs() {
        return service.listAlertConfigs();
    }

    @PostMapping("/alert-configs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('lot:write')")
    public LotDto.AlertConfigResponse createAlertConfig(@Valid @RequestBody LotDto.AlertConfigRequest req) {
        return service.saveAlertConfig(null, req.daysBeforeExpiry(),
                req.severity(), req.notifyRoles(), req.enabled());
    }

    @PutMapping("/alert-configs/{id}")
    @PreAuthorize("hasAuthority('lot:write')")
    public LotDto.AlertConfigResponse updateAlertConfig(
            @PathVariable UUID id,
            @Valid @RequestBody LotDto.AlertConfigRequest req) {
        return service.saveAlertConfig(id, req.daysBeforeExpiry(),
                req.severity(), req.notifyRoles(), req.enabled());
    }
}
