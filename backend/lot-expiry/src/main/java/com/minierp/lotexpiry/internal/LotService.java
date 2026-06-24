package com.minierp.lotexpiry.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.catalog.api.ProductSnapshot;
import com.minierp.catalog.api.VariantLookup;
import com.minierp.catalog.api.VariantView;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.inventory.api.StockMovementDto;
import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.lotexpiry.api.LotAllocation;
import com.minierp.lotexpiry.api.LotDto;
import com.minierp.lotexpiry.api.LotOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.persistence.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LotService implements LotOperations {

    private final ProductLotRepository lots;
    private final LotMovementRepository movements;
    private final ExpiredLotDestructionRepository destructions;
    private final ExpiryAlertConfigRepository alertConfigs;
    private final CatalogLookup catalog;
    private final VariantLookup variants;
    private final DocumentRenderer documentRenderer;
    private final StockOperations stockOps;

    // ─── LotOperations (public API used by other modules) ──────────────────

    @Override
    @Transactional(readOnly = true)
    public List<LotAllocation> selectFEFO(UUID variantId, UUID warehouseId, BigDecimal requestedQty) {
        List<ProductLot> available = lots
                .findByProductVariantIdAndWarehouseIdAndStatusOrderByExpirationDateAsc(
                        variantId, warehouseId, LotStatus.ACTIVE);

        List<LotAllocation> allocations = new ArrayList<>();
        BigDecimal remaining = requestedQty;

        for (ProductLot lot : available) {
            if (remaining.signum() <= 0) break;
            if (lot.getExpirationDate().isBefore(LocalDate.now())) continue; // skip expired
            BigDecimal taken = remaining.min(lot.getQuantityRemaining());
            if (taken.signum() > 0) {
                allocations.add(new LotAllocation(lot.getId(), taken));
                remaining = remaining.subtract(taken);
            }
        }

        if (remaining.signum() > 0) {
            throw new BusinessException("error.lot.insufficient_lot_stock",
                    Map.of("variantId", variantId, "shortage", remaining));
        }
        return allocations;
    }

    @Override
    @Transactional
    public void consumeAllocations(List<LotAllocation> allocations,
                                   String referenceType, UUID referenceId) {
        for (LotAllocation alloc : allocations) {
            ProductLot lot = lots.findById(alloc.lotId())
                    .orElseThrow(() -> NotFoundException.of("entity.lot", alloc.lotId()));
            lot.setQuantityRemaining(lot.getQuantityRemaining().subtract(alloc.quantity()));
            if (lot.getQuantityRemaining().signum() <= 0) {
                lot.setStatus(LotStatus.EXHAUSTED);
            }
            movements.save(LotMovement.builder()
                    .lotId(lot.getId())
                    .type(LotMovementType.SALE_OUT)
                    .quantity(alloc.quantity())
                    .uomId(lot.getUomId())
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .build());
        }
    }

    @Override
    @Transactional
    public UUID receiveLot(UUID variantId, UUID warehouseId, UUID uomId,
                           String lotNumber, LocalDate expirationDate,
                           LocalDate productionDate, BigDecimal quantity,
                           BigDecimal unitCost, UUID supplierId, UUID purchaseOrderId) {
        VariantView variant = variants.require(variantId);
        ProductLot lot = ProductLot.builder()
                .productId(variant.productId())
                .productVariantId(variantId)
                .warehouseId(warehouseId)
                .uomId(uomId)
                .lotNumber(lotNumber)
                .expirationDate(expirationDate)
                .productionDate(productionDate)
                .initialQuantity(quantity)
                .quantityRemaining(quantity)
                .purchaseUnitCost(unitCost)
                .partyId(supplierId)
                .purchaseOrderId(purchaseOrderId)
                .status(LotStatus.ACTIVE)
                .build();
        lots.save(lot);
        movements.save(LotMovement.builder()
                .lotId(lot.getId())
                .type(LotMovementType.RECEIPT)
                .quantity(quantity)
                .uomId(uomId)
                .build());
        return lot.getId();
    }

    /**
     * Opening balance for an expiry-tracked product: create the lot AND post the
     * OPENING_BALANCE stock-in so the Stock row and the lot ledger stay in sync —
     * mirrors what reception does. Lives here because this module is the only one
     * (besides purchase) that can orchestrate both lots and stock without a cycle.
     */
    @Transactional
    public StockMovementDto openingBalanceWithLot(UUID warehouseId, UUID variantId,
                                                  BigDecimal quantity, BigDecimal unitCost,
                                                  String lotNumber, LocalDate expirationDate,
                                                  LocalDate productionDate, String note, UUID userId) {
        VariantView variant = variants.require(variantId);
        ProductSnapshot product = catalog.findProductById(variant.productId())
                .orElseThrow(() -> NotFoundException.of("entity.product", variant.productId()));
        // Defensive: a non-expiry product would never reach this path from the UI,
        // but if it does, post the opening stock without a lot rather than failing.
        if (!product.trackExpiry()) {
            return stockOps.receive(warehouseId, variantId, quantity, unitCost,
                    StockMovementType.OPENING_BALANCE, null, null, null, note, userId);
        }
        if (lotNumber == null || lotNumber.isBlank() || expirationDate == null) {
            throw new BusinessException("error.reception.lot_data_required",
                    Map.of("variantId", variantId));
        }
        UUID lotId = receiveLot(variantId, warehouseId, product.baseUomId(),
                lotNumber, expirationDate, productionDate, quantity, unitCost, null, null);
        return stockOps.receive(warehouseId, variantId, quantity, unitCost,
                StockMovementType.OPENING_BALANCE,
                "LOT", lotId, lotNumber, note, userId);
    }

    @Override
    @Transactional
    public void blockLot(UUID lotId, String reason) {
        ProductLot lot = loadLotInTenant(lotId);
        lot.setStatus(LotStatus.BLOCKED);
        lot.setBlockedReason(reason);
    }

    /** Tenant-guarded by-id load: {@code findById} bypasses the Hibernate tenant filter. */
    private ProductLot loadLotInTenant(UUID lotId) {
        return TenantGuard.requireSameTenant(lots.findById(lotId),
                () -> NotFoundException.of("entity.lot", lotId));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isTrackingExpiry(UUID productId) {
        return catalog.findProductById(productId)
                .map(p -> p.trackExpiry())
                .orElse(false);
    }

    // ─── Service methods for the REST controller ────────────────────────────

    @Transactional(readOnly = true)
    public Page<LotDto.LotResponse> listLots(UUID variantId, UUID warehouseId, Pageable pageable) {
        Page<ProductLot> page = variantId != null
                ? lots.findByProductVariantId(variantId, pageable)
                : lots.findForDashboard(warehouseId, pageable);
        return page.map(this::toLotDto);
    }

    @Transactional(readOnly = true)
    public LotDto.LotResponse getLot(UUID id) {
        return toLotDto(loadLotInTenant(id));
    }

    @Transactional
    public LotDto.LotResponse createLot(UUID variantId, UUID warehouseId, UUID uomId,
                                        String lotNumber, LocalDate expirationDate,
                                        LocalDate productionDate, BigDecimal quantity,
                                        BigDecimal unitCost, UUID supplierId, String notes) {
        UUID id = receiveLot(variantId, warehouseId, uomId, lotNumber, expirationDate,
                productionDate, quantity, unitCost, supplierId, null);
        ProductLot lot = lots.findById(id).orElseThrow();
        lot.setNotes(notes);
        return toLotDto(lot);
    }

    @Transactional
    public void destroyLot(UUID lotId, BigDecimal qty, String method,
                           BigDecimal cost, String notes, UUID userId) {
        destroyLot(lotId, qty, DestructionMethod.valueOf(method), cost, notes, userId);
    }

    @Transactional
    void destroyLot(UUID lotId, BigDecimal qty, DestructionMethod method,
                    BigDecimal cost, String notes, UUID userId) {
        ProductLot lot = loadLotInTenant(lotId);
        if (qty.compareTo(lot.getQuantityRemaining()) > 0) {
            throw new BusinessException("error.lot.destroy_exceeds_remaining",
                    Map.of("requested", qty, "remaining", lot.getQuantityRemaining()));
        }
        lot.setQuantityRemaining(lot.getQuantityRemaining().subtract(qty));
        lot.setStatus(lot.getQuantityRemaining().signum() <= 0 ? LotStatus.DESTROYED : lot.getStatus());

        destructions.save(ExpiredLotDestruction.builder()
                .lotId(lotId)
                .destructionDate(LocalDate.now())
                .destroyedBy(userId)
                .quantityDestroyed(qty)
                .method(method)
                .cost(cost != null ? cost : BigDecimal.ZERO)
                .notes(notes)
                .build());

        movements.save(LotMovement.builder()
                .lotId(lotId)
                .type(LotMovementType.DESTRUCTION)
                .quantity(qty)
                .uomId(lot.getUomId())
                .notes(notes)
                .build());
    }

    /** CDC §15.4: GET /lots/expiring?days=30 */
    @Transactional(readOnly = true)
    public Page<LotDto.LotResponse> listExpiringWithin(int days, UUID warehouseId, Pageable pageable) {
        LocalDate threshold = LocalDate.now().plusDays(Math.max(0, days));
        return lots.findExpiringWithin(threshold, warehouseId, pageable).map(this::toLotDto);
    }

    /** CDC §15.4: GET /lots/expired */
    @Transactional(readOnly = true)
    public Page<LotDto.LotResponse> listExpired(UUID warehouseId, Pageable pageable) {
        return lots.findExpired(warehouseId, pageable).map(this::toLotDto);
    }

    @Transactional(readOnly = true)
    public List<LotDto.AlertConfigResponse> listAlertConfigs() {
        return alertConfigs.findAll().stream().map(this::toConfigDto).toList();
    }

    @Transactional
    public LotDto.AlertConfigResponse saveAlertConfig(UUID id, int daysBeforeExpiry,
                                                      String severity, String notifyRoles,
                                                      boolean enabled) {
        return saveAlertConfig(id, daysBeforeExpiry, AlertSeverity.valueOf(severity), notifyRoles, enabled);
    }

    @Transactional
    LotDto.AlertConfigResponse saveAlertConfig(UUID id, int daysBeforeExpiry,
                                               AlertSeverity severity, String notifyRoles,
                                               boolean enabled) {
        ExpiryAlertConfig cfg = id != null
                ? alertConfigs.findById(id).orElseThrow(() -> NotFoundException.of("entity.alert_config", id))
                : ExpiryAlertConfig.builder().build();
        cfg.setDaysBeforeExpiry(daysBeforeExpiry);
        cfg.setSeverity(severity);
        cfg.setNotifyRoles(notifyRoles);
        cfg.setEnabled(enabled);
        return toConfigDto(alertConfigs.save(cfg));
    }

    /** CDC §4.1 — generates a product label PDF (50×30mm) with expiration date. */
    @Transactional(readOnly = true)
    public byte[] generateLabelPdf(UUID lotId) {
        ProductLot lot = loadLotInTenant(lotId);
        ProductSnapshot product = catalog.findProductById(lot.getProductId()).orElse(null);
        java.util.Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("productName", product == null ? "Produit" : product.name());
        vars.put("sku", product == null ? "" : product.sku());
        vars.put("lotNumber", lot.getLotNumber());
        vars.put("productionDate", lot.getProductionDate());
        vars.put("expirationDate", lot.getExpirationDate());
        vars.put("quantity", lot.getQuantityRemaining());
        return documentRenderer.renderPdf(PdfRenderRequest.of("expiry-label", vars));
    }

    // ─── DTO mappers ────────────────────────────────────────────────────────

    private LotDto.LotResponse toLotDto(ProductLot lot) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), lot.getExpirationDate());
        return new LotDto.LotResponse(
                lot.getId(), lot.getProductId(), lot.getProductVariantId(), lot.getWarehouseId(),
                lot.getLotNumber(), lot.getProductionDate(), lot.getExpirationDate(),
                lot.getInitialQuantity(), lot.getQuantityRemaining(),
                lot.getUomId(), lot.getStatus().name(), lot.getBlockedReason(),
                lot.getNotes(), days);
    }

    private LotDto.AlertConfigResponse toConfigDto(ExpiryAlertConfig c) {
        return new LotDto.AlertConfigResponse(c.getId(), c.getDaysBeforeExpiry(),
                c.getSeverity().name(), c.getNotifyRoles(), c.isEnabled());
    }
}
