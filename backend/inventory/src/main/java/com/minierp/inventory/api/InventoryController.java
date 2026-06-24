package com.minierp.inventory.api;

import com.minierp.inventory.internal.WarehouseService;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Warehouses, stock and movements")
public class InventoryController {

    private final WarehouseService warehouses;
    private final StockOperations stockOps;

    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('stock:read')")
    public List<WarehouseDto> listWarehouses() {
        return warehouses.list();
    }

    @GetMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('stock:read')")
    public WarehouseDto getWarehouse(@PathVariable UUID id) {
        return warehouses.get(id);
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public WarehouseDto createWarehouse(@Valid @RequestBody CreateWarehouseRequest req) {
        return warehouses.create(req.code(), req.name(), req.address(), req.phone(), req.defaultWarehouse());
    }

    @PatchMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public WarehouseDto updateWarehouse(@PathVariable UUID id, @Valid @RequestBody UpdateWarehouseRequest req) {
        return warehouses.update(id, req.name(), req.address(), req.phone(), req.active());
    }

    @PostMapping("/warehouses/{id}/set-default")
    @PreAuthorize("hasAuthority('warehouse:manage')")
    public WarehouseDto setDefaultWarehouse(@PathVariable UUID id) {
        return warehouses.setDefault(id);
    }

    @GetMapping("/stocks/{warehouseId}/{variantId}")
    @PreAuthorize("hasAuthority('stock:read')")
    public StockDto getStock(@PathVariable UUID warehouseId, @PathVariable UUID variantId) {
        return stockOps.getStock(warehouseId, variantId);
    }

    @GetMapping("/stocks/by-warehouse/{warehouseId}")
    @PreAuthorize("hasAuthority('stock:read')")
    public List<StockDto> listStocksByWarehouse(@PathVariable UUID warehouseId) {
        return stockOps.listByWarehouse(warehouseId);
    }

    @GetMapping("/stocks/by-product")
    @PreAuthorize("hasAuthority('stock:read')")
    public List<ProductStockBreakdownDto> listStockBreakdownByProduct() {
        return stockOps.listStockBreakdownByProduct();
    }

    @PostMapping("/stocks/receive")
    @PreAuthorize("hasAuthority('stock:adjust')")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementDto receive(@Valid @RequestBody ReceiveStockRequest req) {
        return stockOps.receive(req.warehouseId(), req.variantId(), req.qty(), req.unitCost(),
                req.type() == null ? StockMovementType.PURCHASE_RECEIPT : req.type(),
                req.referenceType(), req.referenceId(), req.referenceNumber(),
                req.note(), null);
    }

    @PostMapping("/stocks/issue")
    @PreAuthorize("hasAuthority('stock:adjust')")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementDto issue(@Valid @RequestBody IssueStockRequest req) {
        return stockOps.issue(req.warehouseId(), req.variantId(), req.qty(),
                req.type() == null ? StockMovementType.ADJUSTMENT : req.type(),
                req.referenceType(), req.referenceId(), req.referenceNumber(),
                req.note(), null);
    }

    @PostMapping("/stocks/adjust")
    @PreAuthorize("hasAuthority('stock:adjust')")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementDto adjust(@Valid @RequestBody AdjustStockRequest req) {
        return stockOps.adjust(req.warehouseId(), req.variantId(), req.qtySigned(),
                req.unitCost(),
                req.type() == null ? StockMovementType.ADJUSTMENT : req.type(),
                req.note(), null);
    }

    @GetMapping("/stocks/movements")
    @PreAuthorize("hasAuthority('stock:read')")
    public PageResponse<StockMovementDto> listMovements(
            @RequestParam UUID variantId,
            @RequestParam(required = false) UUID warehouseId,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return stockOps.listMovements(variantId, warehouseId, pageable);
    }

    public record CreateWarehouseRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 500) String address,
            @Size(max = 30) String phone,
            Boolean defaultWarehouse) {}

    public record UpdateWarehouseRequest(
            @Size(max = 200) String name,
            @Size(max = 500) String address,
            @Size(max = 30) String phone,
            Boolean active) {}

    public record ReceiveStockRequest(
            @NotNull UUID warehouseId,
            @NotNull UUID variantId,
            @NotNull @DecimalMin("0.000001") BigDecimal qty,
            @NotNull @DecimalMin("0.00") BigDecimal unitCost,
            StockMovementType type,
            String referenceType,
            UUID referenceId,
            String referenceNumber,
            String note) {}

    public record IssueStockRequest(
            @NotNull UUID warehouseId,
            @NotNull UUID variantId,
            @NotNull @DecimalMin("0.000001") BigDecimal qty,
            StockMovementType type,
            String referenceType,
            UUID referenceId,
            String referenceNumber,
            String note) {}

    public record AdjustStockRequest(
            @NotNull UUID warehouseId,
            @NotNull UUID variantId,
            @NotNull BigDecimal qtySigned,
            BigDecimal unitCost,
            StockMovementType type,
            String note) {}
}
