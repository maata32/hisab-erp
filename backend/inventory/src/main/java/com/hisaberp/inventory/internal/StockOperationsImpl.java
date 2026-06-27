package com.hisaberp.inventory.internal;

import com.hisaberp.catalog.api.VariantLookup;
import com.hisaberp.inventory.api.ProductStockBreakdownDto;
import com.hisaberp.inventory.api.ProductStockBreakdownDto.WarehouseStockEntry;
import com.hisaberp.inventory.api.StockDto;
import com.hisaberp.inventory.api.StockMovementDto;
import com.hisaberp.inventory.api.StockMovementType;
import com.hisaberp.inventory.api.StockOperations;
import com.hisaberp.shared.error.BusinessException;
import com.hisaberp.shared.error.NotFoundException;
import com.hisaberp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
class StockOperationsImpl implements StockOperations {

    private static final int COST_SCALE = 6;

    private final StockRepository stocks;
    private final StockMovementRepository movements;
    private final WarehouseRepository warehouses;
    private final VariantLookup variants;

    @Override
    @Transactional
    public StockMovementDto receive(UUID warehouseId, UUID variantId, BigDecimal qty, BigDecimal unitCost,
                                    StockMovementType type, String referenceType, UUID referenceId,
                                    String referenceNumber, String note, UUID userId) {
        requirePositive(qty, "qty");
        requireNonNegative(unitCost, "unitCost");
        ensureWarehouse(warehouseId);

        Stock stock = lockOrCreate(warehouseId, variantId);

        BigDecimal oldQty = stock.getQtyOnHand();
        BigDecimal oldCost = stock.getAverageCost();
        BigDecimal newQty = oldQty.add(qty);
        BigDecimal newCost;
        if (newQty.signum() == 0) {
            newCost = unitCost;
        } else {
            BigDecimal totalValue = oldQty.multiply(oldCost).add(qty.multiply(unitCost));
            newCost = totalValue.divide(newQty, COST_SCALE, RoundingMode.HALF_UP);
        }
        stock.setQtyOnHand(newQty);
        stock.setAverageCost(newCost);

        return persistMovement(warehouseId, variantId, stock.getProductId(), qty, unitCost, type,
                referenceType, referenceId, referenceNumber, note, userId);
    }

    @Override
    @Transactional
    public StockMovementDto issue(UUID warehouseId, UUID variantId, BigDecimal qty,
                                  StockMovementType type, String referenceType, UUID referenceId,
                                  String referenceNumber, String note, UUID userId) {
        requirePositive(qty, "qty");
        ensureWarehouse(warehouseId);

        Stock stock = lockOrCreate(warehouseId, variantId);
        BigDecimal available = stock.getQtyOnHand().subtract(stock.getQtyReserved());
        if (available.compareTo(qty) < 0) {
            throw new BusinessException("error.inventory.insufficient_stock",
                    Map.of("requested", qty, "available", available, "variantId", variantId));
        }
        stock.setQtyOnHand(stock.getQtyOnHand().subtract(qty));

        return persistMovement(warehouseId, variantId, stock.getProductId(), qty.negate(),
                stock.getAverageCost(), type, referenceType, referenceId, referenceNumber, note, userId);
    }

    @Override
    @Transactional
    public StockMovementDto adjust(UUID warehouseId, UUID variantId, BigDecimal qtySigned,
                                   BigDecimal unitCost, StockMovementType type,
                                   String note, UUID userId) {
        if (qtySigned == null || qtySigned.signum() == 0) {
            throw new BusinessException("error.inventory.invalid_adjustment", Map.of());
        }
        ensureWarehouse(warehouseId);
        Stock stock = lockOrCreate(warehouseId, variantId);

        BigDecimal newQty = stock.getQtyOnHand().add(qtySigned);
        if (newQty.signum() < 0) {
            throw new BusinessException("error.inventory.insufficient_stock",
                    Map.of("requested", qtySigned.abs(), "available", stock.getQtyOnHand()));
        }
        stock.setQtyOnHand(newQty);

        if (type == StockMovementType.OPENING_BALANCE && unitCost != null) {
            stock.setAverageCost(unitCost.setScale(COST_SCALE, RoundingMode.HALF_UP));
        } else if (type == StockMovementType.INVENTORY_COUNT && qtySigned.signum() > 0
                && unitCost != null && unitCost.signum() > 0) {
            BigDecimal oldQty = stock.getQtyOnHand().subtract(qtySigned);
            BigDecimal totalValue = oldQty.multiply(stock.getAverageCost())
                    .add(qtySigned.multiply(unitCost));
            BigDecimal newCost = totalValue.divide(newQty, COST_SCALE, RoundingMode.HALF_UP);
            stock.setAverageCost(newCost);
        }

        return persistMovement(warehouseId, variantId, stock.getProductId(), qtySigned,
                unitCost == null ? stock.getAverageCost() : unitCost,
                type, null, null, null, note, userId);
    }

    @Override
    @Transactional
    public StockMovementDto issueAllowNegative(UUID warehouseId, UUID variantId, BigDecimal qty,
                                               StockMovementType type, String referenceType, UUID referenceId,
                                               String referenceNumber, String note, UUID userId) {
        requirePositive(qty, "qty");
        ensureWarehouse(warehouseId);

        Stock stock = lockOrCreate(warehouseId, variantId);
        BigDecimal newQty = stock.getQtyOnHand().subtract(qty);
        stock.setQtyOnHand(newQty);

        if (newQty.signum() < 0) {
            log.warn("Stock went negative: warehouseId={}, variantId={}, qtyOnHand={}",
                    warehouseId, variantId, newQty);
        }

        return persistMovement(warehouseId, variantId, stock.getProductId(), qty.negate(),
                stock.getAverageCost(), type, referenceType, referenceId, referenceNumber, note, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public StockDto getStock(UUID warehouseId, UUID variantId) {
        Stock s = stocks.findByWarehouseIdAndVariantId(warehouseId, variantId)
                .orElseGet(() -> Stock.builder()
                        .warehouseId(warehouseId).variantId(variantId)
                        .productId(variants.require(variantId).productId()).build());
        return toDto(s);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<StockDto> listByWarehouse(UUID warehouseId) {
        return stocks.findByWarehouseId(warehouseId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductStockBreakdownDto> listStockBreakdownByProduct() {
        List<Warehouse> ordered = warehouses.findByActiveTrue().stream()
                .sorted(Comparator.comparing((Warehouse w) -> w.isDefaultWarehouse() ? 0 : 1)
                        .thenComparing(Warehouse::getCode))
                .toList();
        Set<UUID> activeWarehouseIds = ordered.stream().map(Warehouse::getId).collect(Collectors.toSet());

        // Roll up variant stock to the parent product per warehouse using the denormalized product_id.
        Map<UUID, Map<UUID, BigDecimal>> availableByProduct = new HashMap<>();
        for (Stock s : stocks.findAll()) {
            if (!activeWarehouseIds.contains(s.getWarehouseId())) continue;
            availableByProduct
                    .computeIfAbsent(s.getProductId(), k -> new HashMap<>())
                    .merge(s.getWarehouseId(), s.getQtyOnHand().subtract(s.getQtyReserved()), BigDecimal::add);
        }

        return availableByProduct.entrySet().stream()
                .map(e -> new ProductStockBreakdownDto(
                        e.getKey(),
                        ordered.stream()
                                .map(w -> new WarehouseStockEntry(
                                        w.getId(), w.getCode(), w.getName(),
                                        w.isDefaultWarehouse(),
                                        e.getValue().getOrDefault(w.getId(), BigDecimal.ZERO)))
                                .toList()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<UUID> findDefaultWarehouseId() {
        return warehouses.findFirstByDefaultWarehouseTrue().map(Warehouse::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockMovementDto> listMovements(UUID variantId, UUID warehouseId, Pageable pageable) {
        var page = warehouseId == null
                ? movements.findByVariantIdOrderByOccurredAtDesc(variantId, pageable)
                : movements.findByVariantIdAndWarehouseIdOrderByOccurredAtDesc(variantId, warehouseId, pageable);
        return PageResponse.of(page.map(this::toMovementDto));
    }

    private Stock lockOrCreate(UUID warehouseId, UUID variantId) {
        return stocks.lockByWarehouseAndVariant(warehouseId, variantId)
                .orElseGet(() -> stocks.save(Stock.builder()
                        .warehouseId(warehouseId).variantId(variantId)
                        .productId(variants.require(variantId).productId()).build()));
    }

    private void ensureWarehouse(UUID warehouseId) {
        if (warehouses.findById(warehouseId).isEmpty()) {
            throw NotFoundException.of("entity.warehouse", warehouseId);
        }
    }

    private StockMovementDto persistMovement(UUID warehouseId, UUID variantId, UUID productId,
                                             BigDecimal qtySigned, BigDecimal unitCost, StockMovementType type,
                                             String refType, UUID refId, String refNumber,
                                             String note, UUID userId) {
        StockMovement m = StockMovement.builder()
                .warehouseId(warehouseId)
                .variantId(variantId)
                .productId(productId)
                .type(type)
                .qtySigned(qtySigned)
                .unitCost(unitCost)
                .referenceType(refType)
                .referenceId(refId)
                .referenceNumber(refNumber)
                .note(note)
                .occurredAt(Instant.now())
                .userId(userId)
                .build();
        movements.save(m);
        return toMovementDto(m);
    }

    private StockDto toDto(Stock s) {
        return new StockDto(s.getId(), s.getWarehouseId(), s.getVariantId(), s.getProductId(),
                s.getQtyOnHand(), s.getQtyReserved(),
                s.getQtyOnHand().subtract(s.getQtyReserved()), s.getAverageCost());
    }

    private StockMovementDto toMovementDto(StockMovement m) {
        return new StockMovementDto(m.getId(), m.getWarehouseId(), m.getVariantId(), m.getProductId(),
                m.getType(), m.getQtySigned(), m.getUnitCost(),
                m.getReferenceType(), m.getReferenceId(), m.getReferenceNumber(),
                m.getNote(), m.getOccurredAt(), m.getUserId());
    }

    private static void requirePositive(BigDecimal v, String field) {
        if (v == null || v.signum() <= 0) {
            throw new BusinessException("error.inventory.non_positive",
                    Map.of("field", field));
        }
    }

    private static void requireNonNegative(BigDecimal v, String field) {
        if (v == null || v.signum() < 0) {
            throw new BusinessException("error.inventory.negative",
                    Map.of("field", field));
        }
    }
}
