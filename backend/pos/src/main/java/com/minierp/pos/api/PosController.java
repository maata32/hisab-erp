package com.minierp.pos.api;

import com.minierp.pos.internal.PosService;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.util.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pos")
@RequiredArgsConstructor
@Tag(name = "POS", description = "Sessions, sales, and cash movements")
public class PosController {

    private final PosService posService;

    // ── Sessions ─────────────────────────────────────────────────────────────

    @PostMapping("/sessions")
    @PreAuthorize("hasAuthority('pos:open_session')")
    @ResponseStatus(HttpStatus.CREATED)
    public CashSessionDto openSession(@Valid @RequestBody OpenSessionRequest req) {
        return posService.openSession(req.registerId(), req.openingFloat(), currentUserId());
    }

    @PostMapping("/sessions/{id}/close")
    @PreAuthorize("hasAuthority('pos:close_session')")
    public CashSessionDto closeSession(
            @PathVariable UUID id,
            @Valid @RequestBody CloseSessionRequest req) {
        return posService.closeSession(id, req.countedClosing(), req.note(), currentUserId());
    }

    @GetMapping("/sessions/{id}")
    @PreAuthorize("hasAuthority('pos:operate')")
    public CashSessionDto getSession(@PathVariable UUID id) {
        return posService.getSession(id);
    }

    // ── Sales ────────────────────────────────────────────────────────────────

    @PostMapping("/sales")
    @PreAuthorize("hasAuthority('pos:operate')")
    @ResponseStatus(HttpStatus.CREATED)
    public SaleDto createSale(@Valid @RequestBody CreateSaleRequest req) {
        return posService.createSale(req, currentUserId());
    }

    @PostMapping("/sales/sync")
    @PreAuthorize("hasAuthority('pos:operate')")
    public SyncSalesResponse syncSales(@Valid @RequestBody SyncRequest req) {
        return posService.syncSales(req.sales(), currentUserId());
    }

    @GetMapping("/sales/{id}")
    @PreAuthorize("hasAuthority('pos:operate')")
    public SaleDto getSale(@PathVariable UUID id) {
        return posService.getSale(id);
    }

    @GetMapping("/sessions/{sessionId}/sales")
    @PreAuthorize("hasAuthority('pos:operate')")
    public PageResponse<SaleDto> listSalesBySession(
            @PathVariable UUID sessionId,
            Pageable pageable) {
        return posService.listSalesBySession(sessionId, pageable);
    }

    // ── Stats ────────────────────────────────────────────────────────────────

    @GetMapping("/stats/today")
    @PreAuthorize("hasAuthority('pos:operate') or hasAuthority('admin:read')")
    public SalesTodayResponse salesToday() {
        return new SalesTodayResponse(posService.salesToday());
    }

    public record SalesTodayResponse(java.math.BigDecimal salesToday) {}

    // ── Cash movements ───────────────────────────────────────────────────────

    @PostMapping("/sessions/{id}/cash-in")
    @PreAuthorize("hasAuthority('pos:operate')")
    @ResponseStatus(HttpStatus.CREATED)
    public CashMovementDto cashIn(
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequest req) {
        return posService.cashIn(id, req.amount(), req.reason(), currentUserId());
    }

    @PostMapping("/sessions/{id}/cash-out")
    @PreAuthorize("hasAuthority('pos:operate')")
    @ResponseStatus(HttpStatus.CREATED)
    public CashMovementDto cashOut(
            @PathVariable UUID id,
            @Valid @RequestBody CashMovementRequest req) {
        return posService.cashOut(id, req.amount(), req.reason(), currentUserId());
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record OpenSessionRequest(
            @NotNull UUID registerId,
            @DecimalMin("0.00") BigDecimal openingFloat) {}

    public record CloseSessionRequest(
            @DecimalMin("0.00") BigDecimal countedClosing,
            String note) {}

    public record CashMovementRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String reason) {}

    public record SyncRequest(
            String deviceId,
            @NotEmpty @Valid List<CreateSaleRequest> sales) {}

    private UUID currentUserId() {
        return CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
    }
}
