package com.minierp.delivery.internal;

import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.sales.api.CreditNoteReturnRequestedEvent;
import com.minierp.sales.api.NumberingOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Listens for {@link CreditNoteReturnRequestedEvent} from the sales module and
 * materialises the goods-return as a RETURN-typed delivery (BR-YYYY-XXXXX).
 *
 * Runs in the same transaction as the credit-note creation — the listener is
 * synchronous and joins the publisher's transaction (no @Async, no after-commit
 * phase), so credit note + return BL + stock inflow either all commit or all
 * roll back.
 */
@Component
@RequiredArgsConstructor
class CreditNoteReturnEventListener {

    private final DeliveryRepository deliveries;
    private final DeliveryLineRepository deliveryLines;
    private final NumberingOperations numbering;
    private final StockOperations stockOps;
    private final JdbcTemplate jdbc;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(CreditNoteReturnRequestedEvent event) {
        if (event.lines() == null || event.lines().isEmpty()) return;

        UUID warehouseId = resolveReturnWarehouseIdForInvoice(event.invoiceId());
        if (warehouseId == null) {
            throw new BusinessException("error.delivery.warehouse_missing",
                    Map.of("invoiceId", event.invoiceId()));
        }

        String number = numbering.nextReturnDeliveryNumber();
        Delivery br = Delivery.builder()
                .number(number)
                .partyId(event.partyId())
                .invoiceId(event.invoiceId())
                .warehouseId(warehouseId)
                .type(DeliveryType.RETURN)
                .status(DeliveryStatus.DELIVERED)
                .scheduledDate(LocalDate.now())
                .deliveredAt(Instant.now())
                .notes("Auto-generated from credit note " + event.creditNoteNumber())
                .build();
        deliveries.save(br);

        for (CreditNoteReturnRequestedEvent.ReturnLine rl : event.lines()) {
            deliveryLines.save(DeliveryLine.builder()
                    .deliveryId(br.getId())
                    .productId(rl.productId())
                    .uomId(rl.uomId())
                    .quantityOrdered(rl.quantity())
                    .quantityDelivered(rl.quantity())
                    .status(DeliveryLineStatus.COMPLETED)
                    .snapshotName(rl.productName())
                    .snapshotSku(rl.sku())
                    .build());

            stockOps.receive(warehouseId, rl.productId(), rl.quantity(),
                    rl.unitPrice(), StockMovementType.SALE_RETURN,
                    "DELIVERY", br.getId(), br.getNumber(),
                    "Return delivery " + br.getNumber(), null);
        }
    }

    private UUID resolveReturnWarehouseIdForInvoice(UUID invoiceId) {
        UUID tenant = TenantContext.require();
        List<UUID> ids = jdbc.query("""
                SELECT d.warehouse_id FROM deliveries d
                WHERE d.tenant_id = ? AND d.invoice_id = ? AND d.status <> 'CANCELLED'
                  AND d.type = 'OUTBOUND'
                  AND d.warehouse_id IS NOT NULL
                ORDER BY d.created_at ASC LIMIT 1
                """, (rs, i) -> rs.getObject(1, UUID.class), tenant, invoiceId);
        if (!ids.isEmpty()) return ids.get(0);
        return stockOps.findDefaultWarehouseId().orElse(null);
    }
}
