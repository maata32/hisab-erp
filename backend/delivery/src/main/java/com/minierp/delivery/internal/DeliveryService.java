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
    private final StockOperations stockOps;
    private final TenantLookup tenantLookup;

    @Transactional
    public DeliveryDto.DeliveryResponse create(DeliveryDto.CreateDeliveryRequest req, UUID userId) {
        // Business rule: a BL is anchored to an invoice. The chain is Quote → Invoice → BL.
        if (req.invoiceId() == null) {
            throw new BusinessException("error.delivery.invoice_required", Map.of());
        }
        InvoiceSummary invoice = invoices.findById(req.invoiceId())
                .orElseThrow(() -> NotFoundException.of("entity.invoice", req.invoiceId()));
        if (!invoices.canReceiveDelivery(invoice.id())) {
            throw new BusinessException("error.delivery.invoice_not_shippable",
                    Map.of("invoiceId", invoice.id(), "invoiceNumber", invoice.number(),
                            "status", invoice.status()));
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

        // Business rule: a delivery is recorded all-or-nothing — there is no PARTIAL
        // delivery status. Each line ships its full remaining quantity in one call.
        // Caller-supplied per-line quantities are ignored.
        for (DeliveryLine line : lines) {
            BigDecimal remaining = line.getQuantityOrdered().subtract(line.getQuantityDelivered());
            if (remaining.signum() > 0) {
                stockOps.issue(warehouseId, line.getProductId(), remaining,
                        StockMovementType.SALE,
                        "DELIVERY", d.getId(), d.getNumber(),
                        "Delivery " + d.getNumber(), userId);
                line.setQuantityDelivered(line.getQuantityOrdered());
            }
            line.setStatus(DeliveryLineStatus.COMPLETED);
        }

        d.setStatus(DeliveryStatus.DELIVERED);
        d.setDeliveredAt(Instant.now());
        d.setDeliveredBy(userId);

        if (req.signedBy() != null) d.setSignedBy(req.signedBy());
        if (req.notes() != null) d.setNotes(req.notes());

        if (d.getInvoiceId() != null) {
            Map<UUID, BigDecimal> totalsByProduct = new HashMap<>();
            for (Object[] row : deliveryLines.sumDeliveredByProductForInvoice(d.getInvoiceId())) {
                totalsByProduct.put((UUID) row[0], (BigDecimal) row[1]);
            }
            invoices.recomputeDeliveryStatus(d.getInvoiceId(), totalsByProduct);
        }

        return toDto(d);
    }

    @Transactional
    public DeliveryDto.DeliveryResponse cancel(UUID id) {
        Delivery d = deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id));
        if (d.getStatus() == DeliveryStatus.DELIVERED) {
            throw new BusinessException("error.delivery.already_delivered", Map.of());
        }
        // Refuse to cancel once any stock has left the warehouse — the inventory
        // change is real and a CANCELLED label would silently hide it. Caller must
        // post a stock adjustment / return note instead.
        boolean anyShipped = deliveryLines.findByDeliveryId(id).stream()
                .anyMatch(l -> l.getQuantityDelivered() != null
                        && l.getQuantityDelivered().signum() > 0);
        if (anyShipped) {
            throw new BusinessException("error.delivery.already_shipped",
                    Map.of("deliveryId", d.getId(), "deliveryNumber", d.getNumber()));
        }
        d.setStatus(DeliveryStatus.CANCELLED);
        return toDto(d);
    }

    @Transactional(readOnly = true)
    public DeliveryDto.DeliveryResponse get(UUID id) {
        return toDto(deliveries.findById(id).orElseThrow(() -> NotFoundException.of("entity.delivery", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<DeliveryDto.DeliveryResponse> list(UUID customerId, UUID invoiceId, Pageable pageable) {
        var page = invoiceId != null
                ? deliveries.findByInvoiceId(invoiceId, pageable)
                : customerId != null
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
                d.getId(), d.getNumber(), d.getPartyId(), customerName, d.getInvoiceId(),
                d.getWarehouseId(),
                d.getStatus().name(), d.getType().name(),
                d.getScheduledDate(), d.getDeliveredAt(),
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
        vars.put("isReturn", d.getType() == DeliveryType.RETURN);
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
