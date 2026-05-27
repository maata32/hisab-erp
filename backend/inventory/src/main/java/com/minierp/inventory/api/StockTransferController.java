package com.minierp.inventory.api;

import com.minierp.inventory.internal.StockTransferService;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory/transfers")
@RequiredArgsConstructor
@Tag(name = "Stock Transfers", description = "Inter-warehouse stock transfers")
public class StockTransferController {

    private final StockTransferService service;

    @GetMapping
    @PreAuthorize("hasAuthority('stock:read')")
    public PageResponse<StockTransferDto.TransferResponse> list(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.list(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('stock:read')")
    public StockTransferDto.TransferResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventory:transfer')")
    public StockTransferDto.TransferResponse create(@Valid @RequestBody CreateTransferRequest req) {
        return service.create(req.fromWarehouseId(), req.toWarehouseId(),
                req.scheduledDate(), req.notes(),
                req.lines().stream().map(l -> new StockTransferService.LineRequest(
                        l.productId(), l.lotId(), l.uomId(), l.quantityRequested())).toList());
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('inventory:transfer')")
    public StockTransferDto.TransferResponse execute(@PathVariable UUID id) {
        return service.execute(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('inventory:transfer')")
    public StockTransferDto.TransferResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }

    public record CreateTransferRequest(
            @NotNull UUID fromWarehouseId,
            @NotNull UUID toWarehouseId,
            LocalDate scheduledDate,
            String notes,
            @NotNull List<LineReq> lines) {}

    public record LineReq(
            @NotNull UUID productId,
            UUID lotId,
            @NotNull UUID uomId,
            @NotNull BigDecimal quantityRequested) {}
}
