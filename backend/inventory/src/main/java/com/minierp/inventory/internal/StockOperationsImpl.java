package com.minierp.inventory.internal;

import com.minierp.inventory.api.StockDto;
import com.minierp.inventory.api.StockMovementDto;
import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class StockOperationsImpl implements StockOperations {

    private static final int COST_SCALE = 6;

    private final StockRepository stocks;
    private final StockMovementRepository movements;
    private final WarehouseRepository warehouses;

    @Override
    @Transactional
    public StockMovementDto receive(UUID warehouseId, UUID productId, BigDecimal qty, BigDecimal unitCost,
                                    StockMovementType type, String referenceType, UUID referenceId,
                                    String referenceNumber, String note, UUID userId) {
        requirePositive(qty, "qty");
        requireNonNegative(unitCost, "unitCost");
        ensureWarehouse(warehouseId);

        Stock stock = lockOrCreate(warehouseId, productId);

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

        return persistMovement(warehouseId, productId, qty, unitCost, type,
                referenceType, referenceId, referenceNumber, note, userId);
    }

    @Override
    @Transactional
    public StockMovementDto issue(UUID warehouseId, UUID productId, BigDecimal qty,
                                  StockMovementType type, String referenceType, UUID referenceId,
                                  String referenceNumber, String note, UUID userId) {
        requirePositive(qty, "qty");
        ensureWarehouse(warehouseId);

        Stock stock = lockOrCreate(warehouseId, productId);
        BigDecimal available = stock.getQtyOnHand().subtract(stock.getQtyReserved());
        if (available.compareTo(qty) < 0) {
            throw new BusinessException("error.inventory.insufficient_stock",
                    Map.of("requested", qty, "available", available, "productId", productId));
        }
        stock.setQtyOnHand(stock.getQtyOnHand().subtract(qty));

        return persistMovement(warehouseId, productId, qty.negate(), stock.getAverageCost(), type,
                referenceType, referenceId, referenceNumber, note, userId);
    }

    @Override
    @Transactional
    public StockMovementDto adjust(UUID warehouseId, UUID productId, BigDecimal qtySigned,
                                   BigDecimal unitCost, StockMovementType type,
                                   String note, UUID userId) {
        if (qtySigned == null || qtySigned.signum() == 0) {
            throw new BusinessException("error.inventory.invalid_adjustment", Map.of());
        }
        ensureWarehouse(warehouseId);
        Stock stock = lockOrCreate(warehouseId, productId);

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

        return persistMovement(warehouseId, productId, qtySigned,
                unitCost == null ? stock.getAverageCost() : unitCost,
                type, null, null, null, note, userId);
    }

    @Override
    @Transactional
    public StockMovementDto issueAllowNegative(UUID warehouseId, UUID productId, BigDecimal qty,
                                               StockMovementType type, String referenceType, UUID referenceId,
                                               String referenceNumber, String note, UUID userId) {
        requirePositive(qty, "qty");
        ensureWarehouse(warehouseId);

        Stock stock = lockOrCreate(warehouseId, productId);
        BigDecimal newQty = stock.getQtyOnHand().subtract(qty);
        stock.setQtyOnHand(newQty);

        if (newQty.signum() < 0) {
            log.warn("Stock went negative: warehouseId={}, productId={}, qtyOnHand={}",
                    warehouseId, productId, newQty);
        }

        return persistMovement(warehouseId, productId, qty.negate(), stock.getAverageCost(), type,
                referenceType, referenceId, referenceNumber, note, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public StockDto getStock(UUID warehouseId, UUID productId) {
        Stock s = stocks.findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseGet(() -> Stock.builder()
                        .warehouseId(warehouseId).productId(productId).build());
        return new StockDto(s.getId(), s.getWarehouseId(), s.getProductId(),
                s.getQtyOnHand(), s.getQtyReserved(),
                s.getQtyOnHand().subtract(s.getQtyReserved()), s.getAverageCost());
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<StockDto> listByWarehouse(UUID warehouseId) {
        return stocks.findByWarehouseId(warehouseId).stream()
                .map(s -> new StockDto(s.getId(), s.getWarehouseId(), s.getProductId(),
                        s.getQtyOnHand(), s.getQtyReserved(),
                        s.getQtyOnHand().subtract(s.getQtyReserved()), s.getAverageCost()))
                .toList();
    }

    private Stock lockOrCreate(UUID warehouseId, UUID productId) {
        return stocks.lockByWarehouseAndProduct(warehouseId, productId)
                .orElseGet(() -> stocks.save(Stock.builder()
                        .warehouseId(warehouseId).productId(productId).build()));
    }

    private void ensureWarehouse(UUID warehouseId) {
        if (warehouses.findById(warehouseId).isEmpty()) {
            throw NotFoundException.of("entity.warehouse", warehouseId);
        }
    }

    private StockMovementDto persistMovement(UUID warehouseId, UUID productId, BigDecimal qtySigned,
                                             BigDecimal unitCost, StockMovementType type,
                                             String refType, UUID refId, String refNumber,
                                             String note, UUID userId) {
        StockMovement m = StockMovement.builder()
                .warehouseId(warehouseId)
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
        return new StockMovementDto(m.getId(), m.getWarehouseId(), m.getProductId(), m.getType(),
                m.getQtySigned(), m.getUnitCost(),
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
