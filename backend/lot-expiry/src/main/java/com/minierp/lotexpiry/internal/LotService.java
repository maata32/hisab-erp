package com.minierp.lotexpiry.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.lotexpiry.api.LotAllocation;
import com.minierp.lotexpiry.api.LotDto;
import com.minierp.lotexpiry.api.LotOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
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

    // ─── LotOperations (public API used by other modules) ──────────────────

    @Override
    @Transactional(readOnly = true)
    public List<LotAllocation> selectFEFO(UUID productId, UUID warehouseId, BigDecimal requestedQty) {
        List<ProductLot> available = lots
                .findByProductIdAndWarehouseIdAndStatusOrderByExpirationDateAsc(
                        productId, warehouseId, LotStatus.ACTIVE);

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
                    Map.of("productId", productId, "shortage", remaining));
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
    public UUID receiveLot(UUID productId, UUID warehouseId, UUID uomId,
                           String lotNumber, LocalDate expirationDate,
                           LocalDate productionDate, BigDecimal quantity,
                           BigDecimal unitCost, UUID supplierId, UUID purchaseOrderId) {
        ProductLot lot = ProductLot.builder()
                .productId(productId)
                .warehouseId(warehouseId)
                .uomId(uomId)
                .lotNumber(lotNumber)
                .expirationDate(expirationDate)
                .productionDate(productionDate)
                .initialQuantity(quantity)
                .quantityRemaining(quantity)
                .purchaseUnitCost(unitCost)
                .supplierId(supplierId)
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

    @Override
    @Transactional
    public void blockLot(UUID lotId, String reason) {
        ProductLot lot = lots.findById(lotId)
                .orElseThrow(() -> NotFoundException.of("entity.lot", lotId));
        lot.setStatus(LotStatus.BLOCKED);
        lot.setBlockedReason(reason);
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
    public Page<LotDto.LotResponse> listLots(UUID productId, UUID warehouseId, Pageable pageable) {
        Page<ProductLot> page = productId != null
                ? lots.findByProductId(productId, pageable)
                : lots.findForDashboard(warehouseId, pageable);
        return page.map(this::toLotDto);
    }

    @Transactional(readOnly = true)
    public LotDto.LotResponse getLot(UUID id) {
        return toLotDto(lots.findById(id).orElseThrow(() -> NotFoundException.of("entity.lot", id)));
    }

    @Transactional
    public LotDto.LotResponse createLot(UUID productId, UUID warehouseId, UUID uomId,
                                        String lotNumber, LocalDate expirationDate,
                                        LocalDate productionDate, BigDecimal quantity,
                                        BigDecimal unitCost, UUID supplierId, String notes) {
        UUID id = receiveLot(productId, warehouseId, uomId, lotNumber, expirationDate,
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
        ProductLot lot = lots.findById(lotId)
                .orElseThrow(() -> NotFoundException.of("entity.lot", lotId));
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

    // ─── DTO mappers ────────────────────────────────────────────────────────

    private LotDto.LotResponse toLotDto(ProductLot lot) {
        long days = ChronoUnit.DAYS.between(LocalDate.now(), lot.getExpirationDate());
        return new LotDto.LotResponse(
                lot.getId(), lot.getProductId(), lot.getWarehouseId(),
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
