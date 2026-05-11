package com.minierp.inventory.api;

import com.minierp.inventory.internal.InventoryCountService;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/counts")
@RequiredArgsConstructor
@Tag(name = "Inventory Counts", description = "Physical inventory counts with discrepancy adjustments")
public class InventoryCountController {

    private final InventoryCountService service;

    @GetMapping
    @PreAuthorize("hasAuthority('stock:read')")
    public PageResponse<InventoryCountDto.CountResponse> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(service.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('stock:read')")
    public InventoryCountDto.CountResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory:count')")
    public InventoryCountDto.CountResponse create(@Valid @RequestBody CreateCountRequest req) {
        return service.create(req.warehouseId(), req.countDate(), req.notes());
    }

    @PatchMapping("/{id}/lines")
    @PreAuthorize("hasAuthority('inventory:count')")
    public InventoryCountDto.CountResponse updateCounts(
            @PathVariable UUID id,
            @Valid @RequestBody List<LineUpdateRequest> updates) {
        return service.updateCounts(id, updates.stream()
                .map(u -> new InventoryCountService.LineCountUpdate(u.lineId(), u.countedQty()))
                .toList());
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('inventory:count')")
    public InventoryCountDto.CountResponse validate(@PathVariable UUID id) {
        return service.validate(id);
    }

    public record CreateCountRequest(
            @NotNull UUID warehouseId,
            LocalDate countDate,
            String notes) {}

    public record LineUpdateRequest(
            @NotNull UUID lineId,
            @NotNull BigDecimal countedQty) {}
}
