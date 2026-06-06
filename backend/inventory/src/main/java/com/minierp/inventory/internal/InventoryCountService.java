package com.minierp.inventory.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.catalog.api.ProductSnapshot;
import com.minierp.inventory.api.InventoryCountDto;
import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryCountService {

    private final InventoryCountRepository counts;
    private final WarehouseRepository warehouses;
    private final StockRepository stocks;
    private final StockOperations stockOps;
    private final CatalogLookup catalog;

    @Transactional(readOnly = true)
    public Page<InventoryCountDto.CountResponse> list(Pageable pageable) {
        return counts.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public InventoryCountDto.CountResponse get(UUID id) {
        return toDto(counts.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.inventory_count", id)));
    }

    @Transactional
    public InventoryCountDto.CountResponse create(UUID warehouseId, LocalDate countDate, String notes) {
        if (warehouses.findById(warehouseId).isEmpty()) {
            throw NotFoundException.of("entity.warehouse", warehouseId);
        }
        int year = Year.now().getValue();
        long seq = counts.count() + 1;
        String number = String.format("CNT-%d-%05d", year, seq);

        InventoryCount count = InventoryCount.builder()
                .countNumber(number)
                .warehouseId(warehouseId)
                .countDate(countDate != null ? countDate : LocalDate.now())
                .notes(notes)
                .status(InventoryCountStatus.DRAFT)
                .build();

        List<Stock> stockRows = stocks.findByWarehouseId(warehouseId);
        Map<UUID, UUID> baseUomByProduct = catalog.findProductsByIds(
                        stockRows.stream().map(Stock::getProductId).toList())
                .stream()
                .collect(Collectors.toMap(ProductSnapshot::id, ProductSnapshot::baseUomId));

        stockRows.forEach(s -> {
            UUID uomId = baseUomByProduct.get(s.getProductId());
            if (uomId == null) {
                throw NotFoundException.of("entity.product", s.getProductId());
            }
            count.getLines().add(InventoryCountLine.builder()
                    .count(count)
                    .variantId(s.getVariantId())
                    .uomId(uomId)
                    .theoreticalQty(s.getQtyOnHand())
                    .unitCost(s.getAverageCost())
                    .build());
        });

        return toDto(counts.save(count));
    }

    @Transactional
    public InventoryCountDto.CountResponse updateCounts(UUID id, List<LineCountUpdate> updates) {
        InventoryCount count = counts.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.inventory_count", id));
        if (count.getStatus() == InventoryCountStatus.VALIDATED) {
            throw new BusinessException("error.inventory.count_already_validated", Map.of());
        }
        count.setStatus(InventoryCountStatus.IN_PROGRESS);
        updates.forEach(upd -> count.getLines().stream()
                .filter(l -> l.getId().equals(upd.lineId()))
                .findFirst()
                .ifPresent(l -> {
                    l.setCountedQty(upd.countedQty());
                    l.setDiscrepancy(upd.countedQty().subtract(l.getTheoreticalQty()));
                }));
        return toDto(count);
    }

    @Transactional
    public InventoryCountDto.CountResponse validate(UUID id) {
        InventoryCount count = counts.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.inventory_count", id));
        if (count.getStatus() == InventoryCountStatus.VALIDATED) {
            throw new BusinessException("error.inventory.count_already_validated", Map.of());
        }

        UUID userId = CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);

        count.getLines().stream()
                .filter(l -> l.getCountedQty() != null && l.getDiscrepancy() != null
                        && l.getDiscrepancy().signum() != 0)
                .forEach(l -> stockOps.adjust(count.getWarehouseId(), l.getVariantId(),
                        l.getDiscrepancy(), l.getUnitCost(),
                        StockMovementType.INVENTORY_COUNT,
                        "Count " + count.getCountNumber(), userId));

        count.setStatus(InventoryCountStatus.VALIDATED);
        count.setValidatedAt(Instant.now());
        count.setValidatedBy(userId);
        return toDto(count);
    }

    private InventoryCountDto.CountResponse toDto(InventoryCount c) {
        return new InventoryCountDto.CountResponse(
                c.getId(), c.getCountNumber(), c.getWarehouseId(),
                c.getStatus().name(), c.getCountDate(), c.getValidatedAt(), c.getValidatedBy(),
                c.getNotes(),
                c.getLines().stream().map(l -> new InventoryCountDto.LineResponse(
                        l.getId(), l.getVariantId(), l.getLotId(), l.getUomId(),
                        l.getTheoreticalQty(), l.getCountedQty(), l.getDiscrepancy(),
                        l.getUnitCost(), l.getNotes()
                )).toList());
    }

    public record LineCountUpdate(UUID lineId, BigDecimal countedQty) {}
}
