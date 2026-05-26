package com.minierp.delivery.internal;

import com.minierp.partner.api.PartnerLookup;
import com.minierp.partner.api.PartnerSummary;
import com.minierp.delivery.api.DeliveryDto;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.sales.api.InvoiceOperations;
import com.minierp.sales.api.InvoiceSummary;
import com.minierp.sales.api.NumberingOperations;
import com.minierp.sales.api.OrderOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.shared.util.PageResponse;
import com.minierp.tenant.api.TenantLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final DeliveryRepository deliveries;
    private final DeliveryLineRepository deliveryLines;
    private final PartnerLookup customerLookup;
    private final NumberingOperations numbering;
    private final DocumentRenderer renderer;
    private final InvoiceOperations invoices;
    private final OrderOperations orderOps;
    private final StockOperations stockOps;
    private final TenantLookup tenantLookup;

    @Transactional
    public DeliveryDto.DeliveryResponse create(DeliveryDto.CreateDeliveryRequest req, UUID userId) {
        // Business rule (CDC): no delivery without a prior non-cancelled invoice.
        // Resolve the invoice from the order if it wasn't explicitly provided.
        UUID invoiceLookupId = req.invoiceId();
        if (invoiceLookupId == null && req.orderId() != null) {
            invoiceLookupId = invoices.findByOrderId(req.orderId())
                    .map(InvoiceSummary::id)
                    .orElse(null);
        }
        if (invoiceLookupId == null) {
            throw new BusinessException("error.delivery.invoice_required",
                    req.orderId() != null ? Map.of("orderId", req.orderId()) : Map.of());
        }
        final UUID resolvedInvoiceId = invoiceLookupId;
        InvoiceSummary invoice = invoices.findById(resolvedInvoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.invoice", resolvedInvoiceId));
        if ("CANCELLED".equals(invoice.status())) {
            throw new BusinessException("error.delivery.invoice_cancelled",
                    Map.of("invoiceId", invoice.id(), "invoiceNumber", invoice.number()));
        }
        UUID customerId = req.customerId() != null ? req.customerId() : invoice.customerId();
        if (req.customerId() != null && !req.customerId().equals(invoice.customerId())) {
            throw new BusinessException("error.delivery.customer_mismatch",
                    Map.of("requested", req.customerId(), "invoiceCustomer", invoice.customerId()));
        }
        customerLookup.findById(customerId)
                .orElseThrow(() -> NotFoundException.of("entity.customer", customerId));

        // Fall back to the tenant's default warehouse if the caller didn't specify one.
        UUID warehouseId = req.warehouseId();
        if (warehouseId == null) {
            warehouseId = stockOps.findDefaultWarehouseId()
                    .orElseThrow(() -> new BusinessException("error.delivery.warehouse_missing",
                            Map.of()));
        }

        String number = numbering.nextDeliveryNumber();
        Delivery delivery = Delivery.builder()
                .number(number)
                .partyId(customerId)
                .orderId(req.orderId())
                .invoiceId(invoice.id())
                .warehouseId(warehouseId)
                .scheduledDate(req.scheduledDate())
                .address(req.address())
                .contactPhone(req.contactPhone())
                .notes(req.notes())
                .build();
        deliveries.save(delivery);

        if (req.lines() != null) {
            for (DeliveryDto.LineRequest lr : req.lines()) {
                deliveryLines.save(DeliveryLine.builder()
                        .deliveryId(delivery.getId())
                        .productId(lr.productId())
                        .uomId(lr.uomId())
                        .quantityOrdered(lr.quantityOrdered())
                        .snapshotName(lr.productName())
                        .snapshotSku(lr.sku())
                        .build());
            }
        }
        return toDto(delivery);
    }

    @Transactional
    public DeliveryDto.DeliveryResponse startDelivery(UUID id, UUID userId) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id));
        if (d.getStatus() != DeliveryStatus.PENDING) {
            throw new BusinessException("error.delivery.not_pending", Map.of("status", d.getStatus()));
        }
        d.setStatus(DeliveryStatus.IN_PROGRESS);
        return toDto(d);
    }

    @Transactional
    public DeliveryDto.DeliveryResponse recordDelivery(UUID id, DeliveryDto.RecordDeliveryRequest req, UUID userId) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id));
        if (d.getStatus() == DeliveryStatus.DELIVERED || d.getStatus() == DeliveryStatus.CANCELLED) {
            throw new BusinessException("error.delivery.already_terminal", Map.of("status", d.getStatus()));
        }

        List<DeliveryLine> lines = deliveryLines.findByDeliveryId(id);
        UUID warehouseId = d.getWarehouseId();
        if (warehouseId == null) {
            throw new BusinessException("error.delivery.warehouse_missing",
                    Map.of("deliveryId", d.getId()));
        }

        for (DeliveryDto.LineDelivered ld : req.lines()) {
            lines.stream().filter(l -> l.getId().equals(ld.lineId())).findFirst()
                    .ifPresent(line -> {
                        BigDecimal qty = ld.quantityDelivered();
                        if (qty != null && qty.signum() > 0) {
                            stockOps.issue(warehouseId, line.getProductId(), qty,
                                    StockMovementType.SALE,
                                    "DELIVERY", d.getId(), d.getNumber(),
                                    "Delivery " + d.getNumber(), userId);
                        }
                        line.setQuantityDelivered(line.getQuantityDelivered().add(qty));
                        if (line.getQuantityDelivered().compareTo(line.getQuantityOrdered()) >= 0) {
                            line.setStatus(DeliveryLineStatus.COMPLETED);
                        } else {
                            line.setStatus(DeliveryLineStatus.PARTIAL);
                        }
                    });
        }

        boolean allComplete = lines.stream().allMatch(l -> l.getStatus() == DeliveryLineStatus.COMPLETED);
        boolean anyComplete = lines.stream().anyMatch(l -> l.getStatus() != DeliveryLineStatus.PENDING);

        if (allComplete) {
            d.setStatus(DeliveryStatus.DELIVERED);
            d.setDeliveredAt(Instant.now());
            d.setDeliveredBy(userId);
        } else if (anyComplete) {
            d.setStatus(DeliveryStatus.PARTIAL);
        }

        if (req.signedBy() != null) d.setSignedBy(req.signedBy());
        if (req.notes() != null) d.setNotes(req.notes());

        if (d.getOrderId() != null) {
            Map<UUID, BigDecimal> totalsByProduct = new HashMap<>();
            for (Object[] row : deliveryLines.sumDeliveredByProductForOrder(d.getOrderId())) {
                totalsByProduct.put((UUID) row[0], (BigDecimal) row[1]);
            }
            orderOps.recomputeDeliveryStatus(d.getOrderId(), totalsByProduct);
        }

        return toDto(d);
    }

    @Transactional
    public DeliveryDto.DeliveryResponse cancel(UUID id) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id));
        if (d.getStatus() == DeliveryStatus.DELIVERED) {
            throw new BusinessException("error.delivery.already_delivered", Map.of());
        }
        d.setStatus(DeliveryStatus.CANCELLED);
        return toDto(d);
    }

    @Transactional(readOnly = true)
    public DeliveryDto.DeliveryResponse get(UUID id) {
        return toDto(deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryDto.DeliveryResponse> list(UUID customerId, Pageable pageable) {
        var page = customerId != null
                ? deliveries.findByPartyId(customerId, pageable)
                : deliveries.findAll(pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID id) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id));
        PartnerSummary customer = customerLookup.findById(d.getPartyId()).orElse(null);
        List<DeliveryLine> lines = deliveryLines.findByDeliveryId(id);
        Map<String, Object> vars = buildPdfVars(d, lines, customer);
        return renderer.renderPdf(PdfRenderRequest.of("delivery-note", vars));
    }

    private DeliveryDto.DeliveryResponse toDto(Delivery d) {
        List<DeliveryLine> lines = deliveryLines.findByDeliveryId(d.getId());
        String customerName = customerLookup.findById(d.getPartyId()).map(PartnerSummary::name).orElse("");
        return new DeliveryDto.DeliveryResponse(
                d.getId(), d.getNumber(), d.getPartyId(), customerName, d.getOrderId(), d.getInvoiceId(),
                d.getWarehouseId(),
                d.getStatus().name(), d.getScheduledDate(), d.getDeliveredAt(),
                d.getAddress(), d.getContactPhone(), d.getSignedBy(), d.getNotes(),
                lines.stream().map(l -> new DeliveryDto.LineDto(l.getId(), l.getProductId(), l.getUomId(),
                        l.getQuantityOrdered(), l.getQuantityDelivered(), l.getStatus().name(),
                        l.getSnapshotName(), l.getSnapshotSku())).toList(),
                d.getCreatedAt());
    }

    private Map<String, Object> buildPdfVars(Delivery d, List<DeliveryLine> lines, PartnerSummary customer) {
        record LineModel(String productName, String sku, BigDecimal quantityOrdered, BigDecimal quantityDelivered) {}
        record DeliveryModel(String number, java.time.LocalDate scheduledDate, java.time.Instant deliveredAt,
                             String address, String contactPhone, String signedBy, String notes,
                             List<LineModel> lines) {}
        record CustomerModel(String name, String address, String phone) {}
        var lm = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantityOrdered(), l.getQuantityDelivered())).toList();
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("delivery", new DeliveryModel(d.getNumber(), d.getScheduledDate(), d.getDeliveredAt(),
                d.getAddress(), d.getContactPhone(), d.getSignedBy(), d.getNotes(), lm));
        vars.put("customer", new CustomerModel(customer != null ? customer.name() : "", "", customer != null ? customer.phone() : ""));
        return vars;
    }

    private Map<String, Object> brandingVars() {
        var b = tenantLookup.findBrandingById(TenantContext.require()).orElse(null);
        Map<String, Object> m = new HashMap<>();
        m.put("orgName", b == null || b.name() == null ? "" : b.name());
        m.put("orgAddress", b == null || b.address() == null ? "" : b.address());
        m.put("orgPhone", b == null || b.phone() == null ? "" : b.phone());
        m.put("orgEmail", b == null || b.email() == null ? "" : b.email());
        m.put("logoUrl", b == null || b.logoUrl() == null ? "" : b.logoUrl());
        return m;
    }
}
