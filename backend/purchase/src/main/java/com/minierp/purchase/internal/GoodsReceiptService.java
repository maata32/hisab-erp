package com.minierp.purchase.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.catalog.api.ProductSnapshot;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.lotexpiry.api.LotOperations;
import com.minierp.partner.api.PartnerLookup;
import com.minierp.partner.api.PartnerSummary;
import com.minierp.purchase.api.GoodsReceiptDto;
import com.minierp.sales.api.NumberingOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.shared.util.PageResponse;
import com.minierp.tenant.api.TenantLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bon de Réception (BRC) service. Mirror of the sales {@code DeliveryService}:
 * a receipt is anchored to a purchase invoice (chain BC → facture → BRC).
 * INBOUND receipts post PURCHASE_RECEIPT stock-in (+ lots for tracked products)
 * and re-derive the invoice's {@code receptionStatus}; RETURN receipts (created
 * by a purchase credit note) post PURCHASE_RETURN stock-out.
 */
@Service
@RequiredArgsConstructor
public class GoodsReceiptService {

    private final GoodsReceiptRepository receipts;
    private final GoodsReceiptLineRepository receiptLines;
    private final PurchaseInvoiceRepository purchaseInvoices;
    private final PurchaseInvoiceLineRepository purchaseInvoiceLines;
    private final PurchaseCreditNoteRepository purchaseCreditNotes;
    private final PurchaseCreditNoteLineRepository purchaseCreditNoteLines;

    private final PartnerLookup supplierLookup;
    private final CatalogLookup catalog;
    private final StockOperations stockOps;
    private final LotOperations lotOps;
    private final NumberingOperations numbering;
    private final DocumentRenderer renderer;
    private final TenantLookup tenantLookup;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public GoodsReceiptDto.GoodsReceiptResponse create(GoodsReceiptDto.CreateGoodsReceiptRequest req, UUID userId) {
        PurchaseInvoice inv = purchaseInvoices.findById(req.purchaseInvoiceId())
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", req.purchaseInvoiceId()));
        if (!canReceiveReception(inv)) {
            throw new BusinessException("error.reception.invoice_not_receivable",
                    Map.of("invoiceId", inv.getId(), "invoiceNumber", inv.getNumber(), "status", inv.getStatus().name()));
        }
        if (req.supplierId() != null && !req.supplierId().equals(inv.getPartyId())) {
            throw new BusinessException("error.reception.supplier_mismatch",
                    Map.of("requested", req.supplierId(), "invoiceSupplier", inv.getPartyId()));
        }

        UUID warehouseId = req.warehouseId();
        if (warehouseId == null) {
            warehouseId = stockOps.findDefaultWarehouseId()
                    .orElseThrow(() -> new BusinessException("error.reception.warehouse_missing", Map.of()));
        }

        String number = numbering.nextGoodsReceiptNumber();
        GoodsReceipt gr = GoodsReceipt.builder()
                .number(number)
                .partyId(inv.getPartyId())
                .purchaseInvoiceId(inv.getId())
                .warehouseId(warehouseId)
                .type(GoodsReceiptType.INBOUND)
                .scheduledDate(req.scheduledDate())
                .notes(req.notes())
                .build();
        receipts.save(gr);

        List<GoodsReceiptDto.LineRequest> lineReqs = (req.lines() != null && !req.lines().isEmpty())
                ? req.lines()
                : seedLinesFromInvoiceOutstanding(inv.getId());
        for (GoodsReceiptDto.LineRequest lr : lineReqs) {
            if (lr.quantityOrdered() == null || lr.quantityOrdered().signum() <= 0) continue;
            receiptLines.save(GoodsReceiptLine.builder()
                    .goodsReceiptId(gr.getId())
                    .productId(lr.productId())
                    .uomId(lr.uomId())
                    .quantityOrdered(lr.quantityOrdered())
                    .unitCost(lr.unitCost() != null ? lr.unitCost() : BigDecimal.ZERO)
                    .snapshotName(lr.productName())
                    .snapshotSku(lr.sku())
                    .build());
        }
        return toDto(gr);
    }

    /** Seed reception lines from what the invoice still has outstanding (invoiced − already received). */
    private List<GoodsReceiptDto.LineRequest> seedLinesFromInvoiceOutstanding(UUID invoiceId) {
        Map<UUID, BigDecimal> received = sumReceivedByProduct(invoiceId);
        Map<UUID, BigDecimal> acc = new HashMap<>();
        List<GoodsReceiptDto.LineRequest> out = new ArrayList<>();
        for (PurchaseInvoiceLine il : purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(invoiceId)) {
            BigDecimal alreadyAttributed = acc.getOrDefault(il.getProductId(),
                    received.getOrDefault(il.getProductId(), BigDecimal.ZERO));
            BigDecimal remaining = il.getQuantity().subtract(alreadyAttributed).max(BigDecimal.ZERO);
            // never seed more than this line's own quantity
            remaining = remaining.min(il.getQuantity());
            if (remaining.signum() <= 0) continue;
            acc.merge(il.getProductId(), remaining, BigDecimal::add);
            out.add(new GoodsReceiptDto.LineRequest(il.getProductId(), il.getUomId(), remaining,
                    il.getUnitCost(), il.getSnapshotName(), il.getSnapshotSku()));
        }
        return out;
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @Transactional
    public GoodsReceiptDto.GoodsReceiptResponse startReceipt(UUID id, UUID userId) {
        GoodsReceipt gr = receipts.findById(id).orElseThrow(() -> NotFoundException.of("entity.goods_receipt", id));
        if (gr.getStatus() != GoodsReceiptStatus.PENDING) {
            throw new BusinessException("error.reception.not_pending", Map.of("status", gr.getStatus()));
        }
        gr.setStatus(GoodsReceiptStatus.IN_PROGRESS);
        return toDto(gr);
    }

    @Transactional
    public GoodsReceiptDto.GoodsReceiptResponse recordReceipt(UUID id, GoodsReceiptDto.RecordReceiptRequest req, UUID userId) {
        GoodsReceipt gr = receipts.findById(id).orElseThrow(() -> NotFoundException.of("entity.goods_receipt", id));
        if (gr.getStatus() == GoodsReceiptStatus.RECEIVED || gr.getStatus() == GoodsReceiptStatus.CANCELLED) {
            throw new BusinessException("error.reception.already_terminal", Map.of("status", gr.getStatus()));
        }
        if (gr.getType() != GoodsReceiptType.INBOUND) {
            throw new BusinessException("error.reception.not_inbound", Map.of("id", gr.getId()));
        }
        UUID warehouseId = gr.getWarehouseId();
        if (warehouseId == null) {
            throw new BusinessException("error.reception.warehouse_missing", Map.of("id", gr.getId()));
        }

        Map<UUID, GoodsReceiptLine> linesById = new HashMap<>();
        for (GoodsReceiptLine l : receiptLines.findByGoodsReceiptId(id)) linesById.put(l.getId(), l);

        Map<UUID, BigDecimal> invoicedByProduct = invoicedQtyByProduct(gr.getPurchaseInvoiceId());
        Map<UUID, BigDecimal> acc = new HashMap<>(sumReceivedByProduct(gr.getPurchaseInvoiceId()));

        for (GoodsReceiptDto.LineReceived lr : req.lines()) {
            if (lr.quantityReceived() == null || lr.quantityReceived().signum() <= 0) continue;
            GoodsReceiptLine line = linesById.get(lr.lineId());
            if (line == null) {
                throw new BusinessException("error.reception.line_not_in_receipt", Map.of("lineId", lr.lineId()));
            }
            BigDecimal remainingOnLine = line.getQuantityOrdered().subtract(line.getQuantityReceived());
            if (lr.quantityReceived().compareTo(remainingOnLine) > 0) {
                throw new BusinessException("error.reception.over_receipt",
                        Map.of("requested", lr.quantityReceived(), "remaining", remainingOnLine));
            }
            BigDecimal invoiced = invoicedByProduct.getOrDefault(line.getProductId(), BigDecimal.ZERO);
            BigDecimal alreadyForProduct = acc.getOrDefault(line.getProductId(), BigDecimal.ZERO);
            if (alreadyForProduct.add(lr.quantityReceived()).compareTo(invoiced) > 0) {
                throw new BusinessException("error.reception.over_receipt",
                        Map.of("requested", lr.quantityReceived(),
                                "remaining", invoiced.subtract(alreadyForProduct).max(BigDecimal.ZERO)));
            }
            acc.merge(line.getProductId(), lr.quantityReceived(), BigDecimal::add);

            ProductSnapshot product = catalog.findProductById(line.getProductId())
                    .orElseThrow(() -> NotFoundException.of("entity.product", line.getProductId()));
            UUID lotId = null;
            if (product.trackExpiry()) {
                if (lr.lotNumber() == null || lr.lotNumber().isBlank() || lr.expirationDate() == null) {
                    throw new BusinessException("error.reception.lot_data_required",
                            Map.of("productId", product.id()));
                }
                // The lot's purchase_order_id FK references purchase_orders; reception
                // is now invoice-anchored, so there is no PO to link — pass null.
                lotId = lotOps.receiveLot(product.id(), warehouseId, product.baseUomId(),
                        lr.lotNumber(), lr.expirationDate(), lr.productionDate(),
                        lr.quantityReceived(), line.getUnitCost(),
                        gr.getPartyId(), null);
            }
            stockOps.receive(warehouseId, product.id(), lr.quantityReceived(), line.getUnitCost(),
                    StockMovementType.PURCHASE_RECEIPT,
                    "GOODS_RECEIPT", gr.getId(), gr.getNumber(),
                    "BRC " + gr.getNumber(), userId);

            line.setQuantityReceived(line.getQuantityReceived().add(lr.quantityReceived()));
            if (lotId != null) line.setLotId(lotId);
            line.setStatus(line.getQuantityReceived().compareTo(line.getQuantityOrdered()) >= 0
                    ? GoodsReceiptLineStatus.COMPLETED : GoodsReceiptLineStatus.PARTIAL);
        }

        gr.setStatus(GoodsReceiptStatus.RECEIVED);
        gr.setReceivedAt(Instant.now());
        gr.setReceivedBy(userId);
        if (req.notes() != null) gr.setNotes(req.notes());

        recomputeReceptionStatus(gr.getPurchaseInvoiceId());
        return toDto(gr);
    }

    /**
     * Create an INBOUND BRC covering every outstanding line of {@code invoiceId} at
     * full quantity and record it immediately. Rejected for lot-tracked products,
     * which require manual reception to supply lot data.
     */
    @Transactional
    public GoodsReceiptDto.GoodsReceiptResponse receiveImmediately(UUID invoiceId, UUID warehouseId, UUID userId) {
        PurchaseInvoice inv = purchaseInvoices.findById(invoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", invoiceId));
        for (PurchaseInvoiceLine il : purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(invoiceId)) {
            if (lotOps.isTrackingExpiry(il.getProductId())) {
                throw new BusinessException("error.reception.immediate_lot_unsupported",
                        Map.of("productId", il.getProductId()));
            }
        }
        GoodsReceiptDto.GoodsReceiptResponse gr = create(
                new GoodsReceiptDto.CreateGoodsReceiptRequest(inv.getPartyId(), invoiceId, warehouseId,
                        LocalDate.now(), "Réception immédiate", null), userId);
        List<GoodsReceiptDto.LineReceived> recorded = gr.lines().stream()
                .map(l -> new GoodsReceiptDto.LineReceived(l.id(), l.quantityOrdered(), null, null, null))
                .toList();
        return recordReceipt(gr.id(), new GoodsReceiptDto.RecordReceiptRequest(recorded, null), userId);
    }

    @Transactional
    public GoodsReceiptDto.GoodsReceiptResponse cancel(UUID id) {
        GoodsReceipt gr = receipts.findById(id).orElseThrow(() -> NotFoundException.of("entity.goods_receipt", id));
        if (gr.getStatus() == GoodsReceiptStatus.RECEIVED) {
            throw new BusinessException("error.reception.already_received", Map.of());
        }
        boolean anyReceived = receiptLines.findByGoodsReceiptId(id).stream()
                .anyMatch(l -> l.getQuantityReceived() != null && l.getQuantityReceived().signum() > 0);
        if (anyReceived) {
            throw new BusinessException("error.reception.already_posted",
                    Map.of("id", gr.getId(), "number", gr.getNumber()));
        }
        gr.setStatus(GoodsReceiptStatus.CANCELLED);
        return toDto(gr);
    }

    // ── Returns (called by the purchase credit-note flow) ─────────────────────

    /** A product line to send back to the supplier as part of an avoir. */
    record ReturnLine(UUID productId, UUID uomId, BigDecimal quantity, BigDecimal unitCost,
                      String productName, String sku) {}

    /**
     * Materialise a supplier return as a RETURN-typed BRC posting PURCHASE_RETURN
     * stock-out. Mirror of the sales {@code CreditNoteReturnEventListener}, but
     * called directly (same module) by {@code PurchaseService.createPurchaseCreditNote}.
     */
    @Transactional
    public UUID createReturnReceipt(UUID invoiceId, UUID partyId, List<ReturnLine> lines, String creditNoteNumber) {
        if (lines == null || lines.isEmpty()) return null;
        UUID warehouseId = resolveReturnWarehouseIdForInvoice(invoiceId);
        if (warehouseId == null) {
            throw new BusinessException("error.reception.warehouse_missing", Map.of("invoiceId", invoiceId));
        }
        UUID userId = CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
        String number = numbering.nextGoodsReceiptNumber();
        GoodsReceipt gr = GoodsReceipt.builder()
                .number(number)
                .partyId(partyId)
                .purchaseInvoiceId(invoiceId)
                .warehouseId(warehouseId)
                .type(GoodsReceiptType.RETURN)
                .status(GoodsReceiptStatus.RECEIVED)
                .scheduledDate(LocalDate.now())
                .receivedAt(Instant.now())
                .notes("Auto-généré depuis l'avoir " + creditNoteNumber)
                .build();
        receipts.save(gr);
        for (ReturnLine rl : lines) {
            receiptLines.save(GoodsReceiptLine.builder()
                    .goodsReceiptId(gr.getId())
                    .productId(rl.productId())
                    .uomId(rl.uomId())
                    .quantityOrdered(rl.quantity())
                    .quantityReceived(rl.quantity())
                    .unitCost(rl.unitCost())
                    .status(GoodsReceiptLineStatus.COMPLETED)
                    .snapshotName(rl.productName())
                    .snapshotSku(rl.sku())
                    .build());
            stockOps.issue(warehouseId, rl.productId(), rl.quantity(),
                    StockMovementType.PURCHASE_RETURN,
                    "GOODS_RECEIPT", gr.getId(), gr.getNumber(),
                    "Retour fournisseur " + gr.getNumber(), userId);
        }
        return gr.getId();
    }

    private UUID resolveReturnWarehouseIdForInvoice(UUID invoiceId) {
        List<UUID> ids = receipts.findInboundWarehouseIds(invoiceId, PageRequest.of(0, 1));
        if (!ids.isEmpty()) return ids.get(0);
        return stockOps.findDefaultWarehouseId().orElse(null);
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public GoodsReceiptDto.GoodsReceiptResponse get(UUID id) {
        return toDto(receipts.findById(id).orElseThrow(() -> NotFoundException.of("entity.goods_receipt", id)));
    }

    @Transactional(readOnly = true)
    public PageResponse<GoodsReceiptDto.GoodsReceiptResponse> list(UUID supplierId, UUID invoiceId, Pageable pageable) {
        var page = invoiceId != null
                ? receipts.findByPurchaseInvoiceId(invoiceId, pageable)
                : supplierId != null
                    ? receipts.findByPartyId(supplierId, pageable)
                    : receipts.findAll(pageable);
        return PageResponse.of(page.map(this::toDto));
    }

    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID id) {
        GoodsReceipt gr = receipts.findById(id).orElseThrow(() -> NotFoundException.of("entity.goods_receipt", id));
        PartnerSummary supplier = supplierLookup.findById(gr.getPartyId()).orElse(null);
        List<GoodsReceiptLine> lines = receiptLines.findByGoodsReceiptId(id);
        return renderer.renderPdf(PdfRenderRequest.of("goods-receipt", buildPdfVars(gr, lines, supplier)));
    }

    // ── Reception status (intra-module, reads the invoice via the repo) ───────

    private boolean canReceiveReception(PurchaseInvoice inv) {
        return inv.getStatus() != PurchaseInvoiceStatus.DRAFT
                && inv.getStatus() != PurchaseInvoiceStatus.CANCELLED
                && purchaseCreditNotes.countNonDraftByInvoiceId(inv.getId()) == 0
                && inv.getReceptionStatus() != PurchaseInvoiceReceptionStatus.RECEIVED
                && inv.getReceptionStatus() != PurchaseInvoiceReceptionStatus.RETURNED;
    }

    private void recomputeReceptionStatus(UUID invoiceId) {
        PurchaseInvoice inv = purchaseInvoices.findById(invoiceId).orElse(null);
        if (inv == null) return;
        if (inv.getStatus() == PurchaseInvoiceStatus.DRAFT || inv.getStatus() == PurchaseInvoiceStatus.CANCELLED) return;
        // A fully-credited invoice keeps RETURNED once its avoir was issued.
        if (inv.getReceptionStatus() == PurchaseInvoiceReceptionStatus.RETURNED
                && purchaseCreditNotes.countNonDraftByInvoiceId(invoiceId) > 0) return;

        Map<UUID, BigDecimal> invoicedByProduct = invoicedQtyByProduct(invoiceId);
        Map<UUID, BigDecimal> creditedByProduct = aggregate(purchaseCreditNoteLines.sumCreditedByProduct(invoiceId));
        Map<UUID, BigDecimal> receivedByProduct = sumReceivedByProduct(invoiceId);

        boolean allCovered = !invoicedByProduct.isEmpty();
        boolean anyReceived = false;
        for (Map.Entry<UUID, BigDecimal> e : invoicedByProduct.entrySet()) {
            BigDecimal credited = creditedByProduct.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal effectiveInvoiced = e.getValue().subtract(credited).max(BigDecimal.ZERO);
            BigDecimal received = receivedByProduct.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (received.signum() > 0) anyReceived = true;
            if (received.compareTo(effectiveInvoiced) < 0) allCovered = false;
        }
        PurchaseInvoiceReceptionStatus next = allCovered
                ? PurchaseInvoiceReceptionStatus.RECEIVED
                : anyReceived ? PurchaseInvoiceReceptionStatus.PARTIALLY_RECEIVED
                : PurchaseInvoiceReceptionStatus.NONE;
        if (next != inv.getReceptionStatus()) inv.setReceptionStatus(next);
    }

    private Map<UUID, BigDecimal> invoicedQtyByProduct(UUID invoiceId) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (PurchaseInvoiceLine il : purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(invoiceId)) {
            map.merge(il.getProductId(), il.getQuantity(), BigDecimal::add);
        }
        return map;
    }

    private Map<UUID, BigDecimal> sumReceivedByProduct(UUID invoiceId) {
        return aggregate(receiptLines.sumReceivedByProductForInvoice(invoiceId));
    }

    private Map<UUID, BigDecimal> aggregate(List<Object[]> rows) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) map.put((UUID) row[0], (BigDecimal) row[1]);
        return map;
    }

    // ── Mapping / PDF ──────────────────────────────────────────────────────────

    private GoodsReceiptDto.GoodsReceiptResponse toDto(GoodsReceipt gr) {
        List<GoodsReceiptLine> lines = receiptLines.findByGoodsReceiptId(gr.getId());
        String supplierName = supplierLookup.findById(gr.getPartyId()).map(PartnerSummary::name).orElse("");
        return new GoodsReceiptDto.GoodsReceiptResponse(
                gr.getId(), gr.getNumber(), gr.getPartyId(), supplierName, gr.getPurchaseInvoiceId(),
                gr.getWarehouseId(), gr.getStatus().name(), gr.getType().name(),
                gr.getScheduledDate(), gr.getReceivedAt(), gr.getNotes(),
                lines.stream().map(l -> new GoodsReceiptDto.LineDto(l.getId(), l.getProductId(), l.getUomId(),
                        l.getQuantityOrdered(), l.getQuantityReceived(), l.getUnitCost(), l.getLotId(),
                        l.getStatus().name(), l.getSnapshotName(), l.getSnapshotSku())).toList(),
                gr.getCreatedAt());
    }

    private Map<String, Object> buildPdfVars(GoodsReceipt gr, List<GoodsReceiptLine> lines, PartnerSummary supplier) {
        record LineModel(String productName, String sku, BigDecimal quantityOrdered, BigDecimal quantityReceived) {}
        record ReceiptModel(String number, LocalDate scheduledDate, Instant receivedAt, String notes,
                            List<LineModel> lines) {}
        record SupplierModel(String code, String name, String phone, String email) {}
        var lm = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantityOrdered(), l.getQuantityReceived())).toList();
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("receipt", new ReceiptModel(gr.getNumber(), gr.getScheduledDate(), gr.getReceivedAt(), gr.getNotes(), lm));
        vars.put("supplier", new SupplierModel(
                supplier != null ? supplier.code() : "",
                supplier != null ? supplier.name() : "",
                supplier != null ? supplier.phone() : "",
                supplier != null ? supplier.email() : ""));
        vars.put("isReturn", gr.getType() == GoodsReceiptType.RETURN);
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
