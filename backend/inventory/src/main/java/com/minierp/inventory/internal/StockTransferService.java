package com.minierp.inventory.internal;

import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.inventory.api.StockTransferDto;
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

@Service
@RequiredArgsConstructor
public class StockTransferService {

    private final StockTransferRepository transfers;
    private final WarehouseRepository warehouses;
    private final StockOperations stockOps;

    @Transactional(readOnly = true)
    public Page<StockTransferDto.TransferResponse> list(Pageable pageable) {
        return transfers.findAll(pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public StockTransferDto.TransferResponse get(UUID id) {
        return toDto(transfers.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.stock_transfer", id)));
    }

    @Transactional
    public StockTransferDto.TransferResponse create(UUID fromWarehouseId, UUID toWarehouseId,
                                                    LocalDate scheduledDate, String notes,
                                                    List<LineRequest> lineRequests) {
        ensureWarehouse(fromWarehouseId);
        ensureWarehouse(toWarehouseId);
        if (fromWarehouseId.equals(toWarehouseId)) {
            throw new BusinessException("error.inventory.same_warehouse", Map.of());
        }

        String number = generateNumber();
        StockTransfer transfer = StockTransfer.builder()
                .transferNumber(number)
                .fromWarehouseId(fromWarehouseId)
                .toWarehouseId(toWarehouseId)
                .scheduledDate(scheduledDate)
                .notes(notes)
                .status(StockTransferStatus.DRAFT)
                .build();

        lineRequests.forEach(lr -> transfer.getLines().add(
                StockTransferLine.builder()
                        .transfer(transfer)
                        .productId(lr.productId())
                        .lotId(lr.lotId())
                        .uomId(lr.uomId())
                        .quantityRequested(lr.quantityRequested())
                        .build()));

        return toDto(transfers.save(transfer));
    }

    @Transactional
    public StockTransferDto.TransferResponse execute(UUID id) {
        StockTransfer transfer = transfers.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.stock_transfer", id));
        if (transfer.getStatus() != StockTransferStatus.DRAFT
                && transfer.getStatus() != StockTransferStatus.CONFIRMED) {
            throw new BusinessException("error.inventory.transfer_not_executable",
                    Map.of("status", transfer.getStatus()));
        }

        UUID userId = CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);

        transfer.getLines().forEach(line -> {
            BigDecimal qty = line.getQuantityRequested();
            stockOps.issue(transfer.getFromWarehouseId(), line.getProductId(), qty,
                    StockMovementType.TRANSFER_OUT, "STOCK_TRANSFER", transfer.getId(),
                    transfer.getTransferNumber(), null, userId);
            stockOps.receive(transfer.getToWarehouseId(), line.getProductId(), qty,
                    BigDecimal.ZERO, StockMovementType.TRANSFER_IN, "STOCK_TRANSFER",
                    transfer.getId(), transfer.getTransferNumber(), null, userId);
            line.setQuantityTransferred(qty);
        });

        transfer.setStatus(StockTransferStatus.COMPLETED);
        transfer.setCompletedAt(Instant.now());
        return toDto(transfer);
    }

    @Transactional
    public StockTransferDto.TransferResponse cancel(UUID id) {
        StockTransfer transfer = transfers.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.stock_transfer", id));
        if (transfer.getStatus() == StockTransferStatus.COMPLETED) {
            throw new BusinessException("error.inventory.transfer_already_completed", Map.of());
        }
        transfer.setStatus(StockTransferStatus.CANCELLED);
        return toDto(transfer);
    }

    private String generateNumber() {
        int year = Year.now().getValue();
        long seq = transfers.count() + 1;
        return String.format("TRF-%d-%05d", year, seq);
    }

    private void ensureWarehouse(UUID id) {
        if (warehouses.findById(id).isEmpty()) {
            throw NotFoundException.of("entity.warehouse", id);
        }
    }

    private StockTransferDto.TransferResponse toDto(StockTransfer t) {
        return new StockTransferDto.TransferResponse(
                t.getId(), t.getTransferNumber(),
                t.getFromWarehouseId(), t.getToWarehouseId(),
                t.getStatus().name(), t.getScheduledDate(), t.getCompletedAt(),
                t.getNotes(),
                t.getLines().stream().map(l -> new StockTransferDto.LineResponse(
                        l.getId(), l.getProductId(), l.getLotId(), l.getUomId(),
                        l.getQuantityRequested(), l.getQuantityTransferred(), l.getNotes()
                )).toList());
    }

    public record LineRequest(UUID productId, UUID lotId, UUID uomId, BigDecimal quantityRequested) {}
}
