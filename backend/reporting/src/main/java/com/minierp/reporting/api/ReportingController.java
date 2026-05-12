package com.minierp.reporting.api;

import com.minierp.reporting.internal.ReportingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "Operational KPI dashboards")
public class ReportingController {

    private final ReportingService service;

    @GetMapping("/direction")
    @PreAuthorize("hasAuthority('reporting:read')")
    public List<ReportingDto.DirectionRow> direction(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.direction(from, to);
    }

    @GetMapping("/caisse")
    @PreAuthorize("hasAuthority('reporting:read')")
    public List<ReportingDto.CaisseRow> caisse(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.caisse(from, to);
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('reporting:read')")
    public List<ReportingDto.StockRow> stock(
            @RequestParam(required = false) UUID warehouseId) {
        return service.stock(warehouseId);
    }

    @GetMapping("/expiry")
    @PreAuthorize("hasAuthority('reporting:read')")
    public List<ReportingDto.ExpiryRow> expiry(
            @RequestParam(required = false) Integer maxDays) {
        return service.expiry(maxDays);
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('reporting:read')")
    public List<ReportingDto.PaymentRow> payments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.payments(from, to);
    }

    @GetMapping("/deliveries")
    @PreAuthorize("hasAuthority('reporting:read')")
    public List<ReportingDto.DeliveryRow> deliveries() {
        return service.deliveries();
    }

    @GetMapping("/top-products")
    @PreAuthorize("hasAuthority('reporting:read')")
    public List<ReportingDto.TopProductRow> topProducts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month,
            @RequestParam(defaultValue = "10") int limit) {
        return service.topProducts(month, limit);
    }

    /** CDC §15.4 — value of stock at risk by risk bucket (CRITICAL/HIGH/MEDIUM/LOW). */
    @GetMapping("/expiry-risk")
    @PreAuthorize("hasAuthority('reporting:read')")
    public List<ReportingDto.ExpiryRiskRow> expiryRisk(
            @RequestParam(required = false) UUID warehouseId) {
        return service.expiryRisk(warehouseId);
    }

    /** CDC §15.4 — customer aging report. */
    @GetMapping("/aging")
    @PreAuthorize("hasAuthority('reporting:read')")
    public List<ReportingDto.AgingRow> aging() {
        return service.aging();
    }
}
