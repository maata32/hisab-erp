package com.hisaberp.delivery.internal;

import com.hisaberp.partner.api.PartnerLookup;
import com.hisaberp.partner.api.PartnerSummary;
import com.hisaberp.delivery.api.DeliveryDto;
import com.hisaberp.document.api.DocumentRenderer;
import com.hisaberp.document.api.PdfRenderRequest;
import com.hisaberp.inventory.api.StockMovementType;
import com.hisaberp.inventory.api.StockOperations;
import com.hisaberp.lotexpiry.api.LotAllocation;
import com.hisaberp.lotexpiry.api.LotOperations;
import com.hisaberp.sales.api.InvoiceOperations;
import com.hisaberp.sales.api.InvoiceSummary;
import com.hisaberp.sales.api.NumberingOperations;
import com.hisaberp.shared.error.BusinessException;
import com.hisaberp.shared.error.NotFoundException;
import com.hisaberp.shared.persistence.TenantGuard;
import com.hisaberp.shared.tenant.TenantContext;
import com.hisaberp.shared.util.PageResponse;
import com.hisaberp.tenant.api.TenantLookup;
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
public class DeliveryService implements com.hisaberp.delivery.api.DeliveryWriteOperations {

    private final DeliveryRepository deliveries;
    private final DeliveryLineRepository deliveryLines;
    private final PartnerLookup customerLookup;
    private final NumberingOperations numbering;
    private final DocumentRenderer renderer;
    private final InvoiceOperations invoices;
    private final com.hisaberp.sales.api.InvoiceWriteOperations invoiceReader;
    private final StockOperations stockOps;
    private final LotOperations lotOps;
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

        // Server-side cap (BUG-15 / BL-05): a BL cannot ship more than the invoice's
        // remaining-to-deliver per variant (invoiced − already shipped on other BLs).
        // recordDelivery ships each line's full ordered quantity, so capping at create
        // is sufficient. Previously the UI was the only guard → 8 could ship on a qty-5 invoice.
        if (req.lines() != null && !req.lines().isEmpty()) {
            Map<UUID, BigDecimal> invoicedByVariant = new HashMap<>();
            for (var il : invoiceReader.getInvoice(invoice.id()).lines()) {
                if (il.variantId() != null && il.quantity() != null) {
                    invoicedByVariant.merge(il.variantId(), il.quantity(), BigDecimal::add);
                }
            }
            Map<UUID, BigDecimal> deliveredByVariant = new HashMap<>();
            for (Object[] row : deliveryLines.sumDeliveredByVariantForInvoice(invoice.id())) {
                deliveredByVariant.put((UUID) row[0], (BigDecimal) row[1]);
            }
            Map<UUID, BigDecimal> requestedByVariant = new HashMap<>();
            for (DeliveryDto.LineRequest lr : req.lines()) {
                if (lr.variantId() != null && lr.quantityOrdered() != null) {
                    requestedByVariant.merge(lr.variantId(), lr.quantityOrdered(), BigDecimal::add);
                }
            }
            for (Map.Entry<UUID, BigDecimal> e : requestedByVariant.entrySet()) {
                BigDecimal invoiced = invoicedByVariant.get(e.getKey());
                // No invoiced line for this variant → no invoiced quantity to cap against; skip.
                if (invoiced == null) continue;
                BigDecimal already = deliveredByVariant.getOrDefault(e.getKey(), BigDecimal.ZERO);
                BigDecimal remaining = invoiced.subtract(already);
                if (e.getValue().compareTo(remaining) > 0) {
                    throw new BusinessException("error.delivery.exceeds_invoiced",
                            Map.of("variantId", e.getKey(), "requested", e.getValue(),
                                    "remaining", remaining.max(BigDecimal.ZERO)));
                }
            }
        }

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
                        .variantId(lr.variantId())
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
        Delivery d = loadDeliveryInTenant(id);
        if (d.getStatus() != DeliveryStatus.PENDING) {
            throw new BusinessException("error.delivery.not_pending", Map.of("status", d.getStatus()));
        }
        d.setStatus(DeliveryStatus.IN_PROGRESS);
        return toDto(d);
    }

    @Transactional
    public DeliveryDto.DeliveryResponse recordDelivery(UUID id, DeliveryDto.RecordDeliveryRequest req, UUID userId) {
        Delivery d = loadDeliveryInTenant(id);
        if (d.getStatus() == DeliveryStatus.DELIVERED || d.getStatus() == DeliveryStatus.CANCELLED) {
            throw new BusinessException("error.delivery.already_terminal", Map.of("status", d.getStatus()));
        }

        List<DeliveryLine> lines = deliveryLines.findByDeliveryId(id);
        UUID warehouseId = d.getWarehouseId();
        if (warehouseId == null) {
            throw new BusinessException("error.delivery.warehouse_missing",
                    Map.of("deliveryId", d.getId()));
        }

        // Optional manual lot selection per line (overrides FEFO), keyed by delivery line id.
        Map<UUID, List<LotAllocation>> manualLots = new HashMap<>();
        if (req.lines() != null) {
            for (DeliveryDto.LineDelivered ld : req.lines()) {
                if (ld.lineId() != null && ld.lotAllocations() != null && !ld.lotAllocations().isEmpty()) {
                    manualLots.put(ld.lineId(), ld.lotAllocations().stream()
                            .map(a -> new LotAllocation(a.lotId(), a.quantity())).toList());
                }
            }
        }

        // Business rule: a delivery is recorded all-or-nothing — there is no PARTIAL
        // delivery status. Each line ships its full remaining quantity in one call.
        // Caller-supplied per-line quantities are ignored (only optional lot selection is honoured).
        for (DeliveryLine line : lines) {
            BigDecimal remaining = line.getQuantityOrdered().subtract(line.getQuantityDelivered());
            if (remaining.signum() > 0) {
                stockOps.issue(warehouseId, line.getVariantId(), remaining,
                        StockMovementType.SALE,
                        "DELIVERY", d.getId(), d.getNumber(),
                        "Delivery " + d.getNumber(), userId);
                List<LotAllocation> manual = manualLots.get(line.getId());
                if (manual != null) {
                    // Manual lot selection (LOT-15) — consume exactly the designated lots.
                    lotOps.consumeExplicitLots(line.getVariantId(), warehouseId, remaining,
                            manual, "DELIVERY", d.getId());
                } else {
                    // Automatic FEFO consumption for lot/expiry-tracked variants (no-op otherwise).
                    lotOps.consumeFefoIfTracked(line.getVariantId(), warehouseId, remaining,
                            "DELIVERY", d.getId());
                }
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

    @Override
    @Transactional
    public UUID shipInvoiceImmediately(UUID invoiceId, UUID warehouseId, UUID userId) {
        com.hisaberp.sales.api.SalesDto.InvoiceDto invoice = invoiceReader.getInvoice(invoiceId);
        if (invoice.lines() == null || invoice.lines().isEmpty()) {
            throw new BusinessException("error.delivery.invoice_has_no_lines",
                    Map.of("invoiceId", invoiceId));
        }
        // Build one BL line per invoiced line, full quantity. Carry the
        // product snapshot through so the BL renders standalone even though
        // it is settled in the same TX.
        List<DeliveryDto.LineRequest> blLines = invoice.lines().stream()
                .map(l -> new DeliveryDto.LineRequest(
                        l.variantId(), l.productId(), l.uomId(), l.quantity(),
                        l.productName(), l.sku()))
                .toList();
        DeliveryDto.CreateDeliveryRequest createReq = new DeliveryDto.CreateDeliveryRequest(
                invoice.customerId(), invoiceId, warehouseId,
                java.time.LocalDate.now(), null, null,
                "Livraison immédiate", blLines);
        DeliveryDto.DeliveryResponse bl = create(createReq, userId);
        startDelivery(bl.id(), userId);
        // recordDelivery ignores per-line quantities; the stub list is required
        // by the DTO contract only.
        List<DeliveryDto.LineDelivered> stub = bl.lines().stream()
                .map(l -> new DeliveryDto.LineDelivered(l.id(), l.quantityOrdered()))
                .toList();
        recordDelivery(bl.id(),
                new DeliveryDto.RecordDeliveryRequest(stub, null, null),
                userId);
        return bl.id();
    }

    @Transactional
    public DeliveryDto.DeliveryResponse cancel(UUID id) {
        Delivery d = loadDeliveryInTenant(id);
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
        return toDto(loadDeliveryInTenant(id));
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
        Delivery d = loadDeliveryInTenant(id);
        PartnerSummary customer = customerLookup.findById(d.getPartyId()).orElse(null);
        List<DeliveryLine> lines = deliveryLines.findByDeliveryId(id);
        Map<String, Object> vars = buildPdfVars(d, lines, customer);
        return renderer.renderPdf(PdfRenderRequest.of("delivery-note", vars));
    }

    /**
     * Load a delivery by id, enforcing it belongs to the current tenant. {@code findById}
     * bypasses the Hibernate tenant filter, so without this guard a token from tenant A could
     * read/modify a delivery of tenant B (BUG-2 / SEC-02).
     */
    private Delivery loadDeliveryInTenant(UUID id) {
        return TenantGuard.requireSameTenant(deliveries.findById(id),
                () -> NotFoundException.of("entity.delivery", id));
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
                lines.stream().map(l -> new DeliveryDto.LineDto(l.getId(), l.getVariantId(), l.getProductId(), l.getUomId(),
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
