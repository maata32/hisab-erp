package com.minierp.purchase.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.catalog.api.ProductSnapshot;
import com.minierp.partner.api.ApBalanceOperations;
import com.minierp.partner.api.PartnerLookup;
import com.minierp.partner.api.PartnerSummary;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.purchase.api.PurchaseCreditNoteAppliedEvent;
import com.minierp.purchase.api.PurchaseDto;
import com.minierp.purchase.api.PurchaseInvoiceOperations;
import com.minierp.purchase.api.PurchaseInvoicePaymentsDetachedEvent;
import com.minierp.purchase.api.PurchaseInvoiceSummary;
import com.minierp.sales.api.NumberingOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.shared.util.PageResponse;
import com.minierp.tenant.api.TenantLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseService implements PurchaseInvoiceOperations {

    private final PurchaseOrderRepository purchaseOrders;
    private final PurchaseOrderLineRepository purchaseOrderLines;
    private final PurchaseInvoiceRepository purchaseInvoices;
    private final PurchaseInvoiceLineRepository purchaseInvoiceLines;
    private final PurchaseCreditNoteRepository purchaseCreditNotes;
    private final PurchaseCreditNoteLineRepository purchaseCreditNoteLines;
    private final GoodsReceiptLineRepository goodsReceiptLines;

    private final PartnerLookup supplierLookup;
    private final ApBalanceOperations supplierBalanceOps;
    private final CatalogLookup catalog;
    private final NumberingOperations numbering;
    private final DocumentRenderer renderer;
    private final TenantLookup tenantLookup;
    private final GoodsReceiptService goodsReceiptService;
    private final ApplicationEventPublisher events;

    // ────────────────────────────────────────────────────────────────────────
    // Purchase orders
    // ────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseDto.PurchaseOrderDto createOrder(PurchaseDto.CreatePurchaseOrderRequest req) {
        PartnerSummary supplier = supplierLookup.findById(req.supplierId())
                .orElseThrow(() -> NotFoundException.of("entity.supplier", req.supplierId()));

        String number = numbering.nextPurchaseOrderNumber();
        PurchaseOrder po = PurchaseOrder.builder()
                .number(number)
                .partyId(req.supplierId())
                .warehouseId(req.warehouseId())
                .orderDate(req.orderDate() != null ? req.orderDate() : LocalDate.now())
                .expectedDate(req.expectedDate())
                .status(PurchaseOrderStatus.DRAFT)
                .currency(req.currency() != null ? req.currency() : supplier.currency())
                .notes(req.notes())
                .build();
        purchaseOrders.save(po);

        List<PurchaseOrderLine> built = buildPoLines(po.getId(), req.lines());
        computePoTotals(po, built);
        return toPoDto(po, built, supplier.name());
    }

    @Transactional(readOnly = true)
    public PurchaseDto.PurchaseOrderDto getOrder(UUID id) {
        PurchaseOrder po = purchaseOrders.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_order", id));
        String name = supplierLookup.findById(po.getPartyId()).map(PartnerSummary::name).orElse("");
        return toPoDto(po, purchaseOrderLines.findByPurchaseOrderIdOrderByLineNumberAsc(id), name);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseDto.PurchaseOrderDto> listOrders(UUID supplierId, String status, Pageable pageable) {
        PurchaseOrderStatus st = status != null && !status.isBlank() ? PurchaseOrderStatus.valueOf(status) : null;
        var page = purchaseOrders.findFiltered(supplierId, st, pageable);
        return PageResponse.of(page.map(po -> {
            String name = supplierLookup.findById(po.getPartyId()).map(PartnerSummary::name).orElse("");
            return toPoDto(po, purchaseOrderLines.findByPurchaseOrderIdOrderByLineNumberAsc(po.getId()), name);
        }));
    }

    @Transactional
    public PurchaseDto.PurchaseOrderDto confirmOrder(UUID id) {
        PurchaseOrder po = purchaseOrders.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_order", id));
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new BusinessException("error.purchase.po_not_draft",
                    Map.of("status", po.getStatus().name()));
        }
        po.setStatus(PurchaseOrderStatus.CONFIRMED);
        String name = supplierLookup.findById(po.getPartyId()).map(PartnerSummary::name).orElse("");
        return toPoDto(po, purchaseOrderLines.findByPurchaseOrderIdOrderByLineNumberAsc(id), name);
    }

    @Transactional
    public PurchaseDto.PurchaseOrderDto cancelOrder(UUID id) {
        PurchaseOrder po = purchaseOrders.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_order", id));
        if (po.getStatus() == PurchaseOrderStatus.CONVERTED) {
            throw new BusinessException("error.purchase.po_already_converted",
                    Map.of("status", po.getStatus().name()));
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        String name = supplierLookup.findById(po.getPartyId()).map(PartnerSummary::name).orElse("");
        return toPoDto(po, purchaseOrderLines.findByPurchaseOrderIdOrderByLineNumberAsc(id), name);
    }

    /**
     * Convert a confirmed/draft purchase order into a DRAFT purchase invoice —
     * mirror of {@code SalesService.convertQuoteToInvoice}. Copies the order
     * lines; the invoice stays DRAFT (it does not hit the AP balance until it is
     * issued). The order is marked CONVERTED.
     */
    @Transactional
    public PurchaseDto.PurchaseInvoiceDto convertOrderToInvoice(UUID orderId, PurchaseDto.ConvertOrderToInvoiceRequest req) {
        PurchaseOrder po = purchaseOrders.findById(orderId)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_order", orderId));
        if (po.getStatus() != PurchaseOrderStatus.DRAFT && po.getStatus() != PurchaseOrderStatus.CONFIRMED) {
            throw new BusinessException("error.purchase.po_not_convertible",
                    Map.of("status", po.getStatus().name()));
        }
        String number = numbering.nextPurchaseInvoiceNumber();
        PurchaseInvoice inv = PurchaseInvoice.builder()
                .number(number)
                .partyId(po.getPartyId())
                .purchaseOrderId(po.getId())
                .supplierReference(req != null ? req.supplierReference() : null)
                .invoiceDate(LocalDate.now())
                .dueDate(req != null ? req.dueDate() : null)
                .status(PurchaseInvoiceStatus.DRAFT)
                .receptionStatus(PurchaseInvoiceReceptionStatus.NONE)
                .currency(po.getCurrency())
                .notes(po.getNotes())
                .build();
        purchaseInvoices.save(inv);

        int i = 1;
        for (PurchaseOrderLine pol : purchaseOrderLines.findByPurchaseOrderIdOrderByLineNumberAsc(orderId)) {
            purchaseInvoiceLines.save(PurchaseInvoiceLine.builder()
                    .purchaseInvoiceId(inv.getId()).lineNumber(i++)
                    .productId(pol.getProductId()).uomId(pol.getUomId())
                    .quantity(pol.getQuantity()).unitCost(pol.getUnitCost())
                    .taxRate(pol.getTaxRate()).lineTotal(pol.getLineTotal())
                    .snapshotName(pol.getSnapshotName()).snapshotSku(pol.getSnapshotSku())
                    .build());
        }
        // DRAFT invoice mirrors the order totals 1:1.
        inv.setSubtotal(po.getSubtotal());
        inv.setTaxAmount(po.getTaxAmount());
        inv.setTotal(po.getTotal());
        inv.setBalance(po.getTotal());

        po.setStatus(PurchaseOrderStatus.CONVERTED);
        po.setConvertedToInvoiceId(inv.getId());

        String name = supplierLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
        return toInvoiceDto(inv, purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(inv.getId()), name);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Purchase invoices
    // ────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseDto.PurchaseInvoiceDto createInvoice(PurchaseDto.CreatePurchaseInvoiceRequest req) {
        PartnerSummary supplier = supplierLookup.findById(req.supplierId())
                .orElseThrow(() -> NotFoundException.of("entity.supplier", req.supplierId()));

        String number = numbering.nextPurchaseInvoiceNumber();
        PurchaseInvoice inv = PurchaseInvoice.builder()
                .number(number)
                .partyId(req.supplierId())
                .purchaseOrderId(req.purchaseOrderId())
                .supplierReference(req.supplierReference())
                .invoiceDate(req.invoiceDate() != null ? req.invoiceDate() : LocalDate.now())
                .dueDate(req.dueDate())
                .status(PurchaseInvoiceStatus.ISSUED)
                .receptionStatus(PurchaseInvoiceReceptionStatus.NONE)
                .currency(req.currency() != null ? req.currency() : supplier.currency())
                .notes(req.notes())
                .build();
        purchaseInvoices.save(inv);

        List<PurchaseInvoiceLine> built = buildInvoiceLines(inv.getId(), req.lines());
        computeInvoiceTotals(inv, built);

        supplierBalanceOps.addToInvoiced(inv.getPartyId(), inv.getTotal());
        return toInvoiceDto(inv, built, supplier.name());
    }

    @Transactional
    public PurchaseDto.PurchaseInvoiceDto issueInvoice(UUID id) {
        PurchaseInvoice inv = purchaseInvoices.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", id));
        if (inv.getStatus() != PurchaseInvoiceStatus.DRAFT) {
            throw new BusinessException("error.purchase.invoice_not_draft",
                    Map.of("status", inv.getStatus().name()));
        }
        inv.setStatus(PurchaseInvoiceStatus.ISSUED);
        supplierBalanceOps.addToInvoiced(inv.getPartyId(), inv.getTotal());
        String name = supplierLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
        return toInvoiceDto(inv, purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(id), name);
    }

    @Transactional(readOnly = true)
    public PurchaseDto.PurchaseInvoiceDto getInvoice(UUID id) {
        PurchaseInvoice inv = purchaseInvoices.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", id));
        String name = supplierLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
        return toInvoiceDto(inv, purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(id), name);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseDto.PurchaseInvoiceDto> listInvoices(UUID supplierId, String status, Pageable pageable) {
        PurchaseInvoiceStatus st = status != null && !status.isBlank() ? PurchaseInvoiceStatus.valueOf(status) : null;
        var page = purchaseInvoices.findFiltered(supplierId, st, pageable);
        List<UUID> ids = page.getContent().stream().map(PurchaseInvoice::getId).toList();
        Map<UUID, Long> counts = creditNoteCounts(ids);
        return PageResponse.of(page.map(inv -> {
            String name = supplierLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
            return toInvoiceDto(inv, purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(inv.getId()),
                    name, counts.getOrDefault(inv.getId(), 0L));
        }));
    }

    @Transactional
    public PurchaseDto.PurchaseInvoiceDto cancelInvoice(UUID id) {
        PurchaseInvoice inv = purchaseInvoices.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", id));
        if (inv.getStatus() != PurchaseInvoiceStatus.DRAFT) {
            throw new BusinessException("error.purchase.invoice_not_draft",
                    Map.of("status", inv.getStatus().name()));
        }
        inv.setStatus(PurchaseInvoiceStatus.CANCELLED);
        String name = supplierLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
        return toInvoiceDto(inv, purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(id), name);
    }

    // ── PurchaseInvoiceOperations ────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<PurchaseInvoiceSummary> findById(UUID id) {
        return purchaseInvoices.findById(id).map(this::toInvoiceSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseInvoiceSummary> findUnpaidBySupplier(UUID supplierId) {
        return purchaseInvoices.findUnpaidByParty(supplierId)
                .stream().map(this::toInvoiceSummary).toList();
    }

    @Override
    @Transactional
    public void applyPayment(UUID purchaseInvoiceId, BigDecimal amount) {
        PurchaseInvoice inv = purchaseInvoices.lockById(purchaseInvoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", purchaseInvoiceId));
        inv.setPaidAmount(inv.getPaidAmount().add(amount));
        inv.setBalance(inv.getTotal().subtract(inv.getPaidAmount()).max(BigDecimal.ZERO));
        if (inv.getBalance().compareTo(BigDecimal.ZERO) == 0) {
            inv.setStatus(PurchaseInvoiceStatus.PAID);
        } else if (inv.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            inv.setStatus(PurchaseInvoiceStatus.PARTIAL);
        }
        supplierBalanceOps.addToPaid(inv.getPartyId(), amount, true);
    }

    @Override
    @Transactional
    public void reversePayment(UUID purchaseInvoiceId, BigDecimal amount) {
        PurchaseInvoice inv = purchaseInvoices.lockById(purchaseInvoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", purchaseInvoiceId));
        BigDecimal newPaid = inv.getPaidAmount().subtract(amount).max(BigDecimal.ZERO);
        inv.setPaidAmount(newPaid);
        inv.setBalance(inv.getTotal().subtract(newPaid).max(BigDecimal.ZERO));
        if (newPaid.signum() == 0) {
            if (inv.getStatus() == PurchaseInvoiceStatus.PAID || inv.getStatus() == PurchaseInvoiceStatus.PARTIAL) {
                inv.setStatus(PurchaseInvoiceStatus.ISSUED);
            }
        } else if (inv.getStatus() == PurchaseInvoiceStatus.PAID) {
            inv.setStatus(PurchaseInvoiceStatus.PARTIAL);
        }
        supplierBalanceOps.addToPaid(inv.getPartyId(), amount.negate(), false);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Purchase credit notes (avoirs) — total-only, mirror of sales credit notes
    // ────────────────────────────────────────────────────────────────────────

    @Transactional
    public PurchaseDto.PurchaseCreditNoteDto createPurchaseCreditNote(UUID invoiceId, PurchaseDto.CreatePurchaseCreditNoteRequest req) {
        PurchaseInvoice inv = purchaseInvoices.findById(invoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", invoiceId));
        ensureCreditable(inv);

        List<PurchaseInvoiceLine> invLines = purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(invoiceId);
        if (invLines.isEmpty()) {
            throw new BusinessException("error.purchase.creditnote.invoice_has_no_lines",
                    Map.of("invoiceId", inv.getId()));
        }
        Map<UUID, BigDecimal> receivedByProduct = aggregate(goodsReceiptLines.sumReceivedByProductForInvoice(invoiceId));

        String number = numbering.nextPurchaseCreditNoteNumber();
        PurchaseCreditNote cn = PurchaseCreditNote.builder()
                .number(number)
                .purchaseInvoiceId(invoiceId)
                .partyId(inv.getPartyId())
                .issueDate(LocalDate.now())
                .reason(req != null ? req.reason() : null)
                .currency(inv.getCurrency())
                .status(PurchaseCreditNoteStatus.ISSUED)
                .build();
        purchaseCreditNotes.save(cn);

        // Total avoir: one line per invoice line, full quantity. The stock return
        // is what has actually been received per product — never-received units
        // cancel without a return BRC.
        Map<UUID, BigDecimal> productReturnedAcc = new HashMap<>();
        int lineNo = 1;
        for (PurchaseInvoiceLine il : invLines) {
            BigDecimal received = receivedByProduct.getOrDefault(il.getProductId(), BigDecimal.ZERO);
            BigDecimal alreadyAttributed = productReturnedAcc.getOrDefault(il.getProductId(), BigDecimal.ZERO);
            BigDecimal stockReturnQty = received.subtract(alreadyAttributed)
                    .max(BigDecimal.ZERO).min(il.getQuantity());
            if (stockReturnQty.signum() > 0) {
                productReturnedAcc.merge(il.getProductId(), stockReturnQty, BigDecimal::add);
            }
            purchaseCreditNoteLines.save(PurchaseCreditNoteLine.builder()
                    .purchaseCreditNoteId(cn.getId()).lineNumber(lineNo++)
                    .purchaseInvoiceLineId(il.getId())
                    .productId(il.getProductId()).uomId(il.getUomId())
                    .quantity(il.getQuantity()).unitCost(il.getUnitCost())
                    .taxRate(il.getTaxRate()).lineTotal(il.getLineTotal())
                    .returnedToStockQty(stockReturnQty)
                    .snapshotName(il.getSnapshotName()).snapshotSku(il.getSnapshotSku())
                    .build());
        }

        // Aggregate per-product return lines for the return BRC.
        List<GoodsReceiptService.ReturnLine> returnLines = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> e : productReturnedAcc.entrySet()) {
            if (e.getValue().signum() <= 0) continue;
            PurchaseInvoiceLine ref = invLines.stream()
                    .filter(l -> l.getProductId().equals(e.getKey()))
                    .findFirst().orElseThrow();
            returnLines.add(new GoodsReceiptService.ReturnLine(
                    ref.getProductId(), ref.getUomId(), e.getValue(),
                    ref.getUnitCost(), ref.getSnapshotName(), ref.getSnapshotSku()));
        }

        // Avoir total = invoice total.
        cn.setSubtotal(inv.getSubtotal());
        cn.setTaxAmount(inv.getTaxAmount());
        cn.setTotal(inv.getTotal());
        cn.setAmount(inv.getTotal());

        // Detach the invoice from its supplier payments: the avoir, not the cash,
        // now settles the invoice. The detachment listener soft-voids the
        // SUPPLIER_PAYMENT → PURCHASE_INVOICE allocation rows so the payment
        // residual reopens as an available open item.
        BigDecimal priorPaid = inv.getPaidAmount();
        if (priorPaid.signum() > 0) {
            inv.setPaidAmount(BigDecimal.ZERO);
            inv.setBalance(inv.getTotal());
            if (inv.getStatus() != PurchaseInvoiceStatus.CANCELLED) {
                inv.setStatus(PurchaseInvoiceStatus.ISSUED);
            }
            supplierBalanceOps.addToPaid(inv.getPartyId(), priorPaid.negate(), false);
            events.publishEvent(new PurchaseInvoicePaymentsDetachedEvent(
                    inv.getId(), inv.getPartyId(), cn.getNumber()));
        }

        // Letter the avoir against the (now fully open) invoice balance.
        BigDecimal imputed = applyCredit(inv, inv.getTotal());
        cn.setAppliedToInvoiceId(inv.getId());
        cn.setStatus(PurchaseCreditNoteStatus.APPLIED);

        if (imputed.signum() > 0) {
            events.publishEvent(new PurchaseCreditNoteAppliedEvent(
                    cn.getId(), cn.getNumber(), inv.getId(), inv.getPartyId(), imputed));
        }

        // Send the received goods back to the supplier (RETURN BRC, stock-out).
        if (!returnLines.isEmpty()) {
            goodsReceiptService.createReturnReceipt(inv.getId(), inv.getPartyId(), returnLines, cn.getNumber());
        }
        inv.setReceptionStatus(returnLines.isEmpty()
                ? PurchaseInvoiceReceptionStatus.NONE
                : PurchaseInvoiceReceptionStatus.RETURNED);

        return toCreditNoteDto(cn);
    }

    @Transactional(readOnly = true)
    public PurchaseDto.PurchaseCreditNoteDto getPurchaseCreditNote(UUID id) {
        PurchaseCreditNote cn = purchaseCreditNotes.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_credit_note", id));
        return toCreditNoteDto(cn);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseDto.PurchaseCreditNoteDto> listPurchaseCreditNotes(UUID supplierId, UUID invoiceId, Pageable pageable) {
        var page = invoiceId != null
                ? purchaseCreditNotes.findByPurchaseInvoiceId(invoiceId, pageable)
                : supplierId != null
                    ? purchaseCreditNotes.findByPartyId(supplierId, pageable)
                    : purchaseCreditNotes.findAll(pageable);
        return PageResponse.of(page.map(this::toCreditNoteDto));
    }

    @Transactional(readOnly = true)
    public PurchaseDto.PurchaseCreditNotePreviewDto getPurchaseCreditNotePreview(UUID invoiceId) {
        PurchaseInvoice inv = purchaseInvoices.findById(invoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", invoiceId));
        String blockReason = creditBlockReason(inv);
        Map<UUID, BigDecimal> receivedByProduct = aggregate(goodsReceiptLines.sumReceivedByProductForInvoice(invoiceId));
        Map<UUID, BigDecimal> acc = new HashMap<>();
        List<PurchaseDto.PurchaseCreditNoteReturnLineDto> returnRows = new ArrayList<>();
        for (PurchaseInvoiceLine il : purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(invoiceId)) {
            BigDecimal received = receivedByProduct.getOrDefault(il.getProductId(), BigDecimal.ZERO);
            BigDecimal alreadyAttributed = acc.getOrDefault(il.getProductId(), BigDecimal.ZERO);
            BigDecimal qty = received.subtract(alreadyAttributed).max(BigDecimal.ZERO).min(il.getQuantity());
            if (qty.signum() <= 0) continue;
            acc.merge(il.getProductId(), qty, BigDecimal::add);
            returnRows.add(new PurchaseDto.PurchaseCreditNoteReturnLineDto(
                    il.getProductId(), il.getSnapshotName(), il.getSnapshotSku(), qty));
        }
        return new PurchaseDto.PurchaseCreditNotePreviewDto(
                inv.getId(), inv.getNumber(), inv.getTotal(), blockReason, inv.getPaidAmount(), returnRows);
    }

    private void ensureCreditable(PurchaseInvoice inv) {
        String reason = creditBlockReason(inv);
        if (reason != null) {
            throw new BusinessException("error.purchase.creditnote.not_creditable",
                    Map.of("reason", reason, "status", inv.getStatus().name()));
        }
    }

    private String creditBlockReason(PurchaseInvoice inv) {
        if (inv.getStatus() == PurchaseInvoiceStatus.DRAFT || inv.getStatus() == PurchaseInvoiceStatus.CANCELLED) {
            return "INVOICE_NOT_CREDITABLE";
        }
        if (purchaseCreditNotes.countNonDraftByInvoiceId(inv.getId()) > 0) {
            return "ALREADY_CREDITED";
        }
        return null;
    }

    private BigDecimal applyCredit(PurchaseInvoice inv, BigDecimal amount) {
        BigDecimal imputed = amount.min(inv.getBalance()).max(BigDecimal.ZERO);
        if (imputed.signum() == 0) return BigDecimal.ZERO;
        inv.setBalance(inv.getBalance().subtract(imputed));
        if (inv.getBalance().compareTo(BigDecimal.ZERO) == 0) {
            inv.setStatus(PurchaseInvoiceStatus.PAID);
        } else if (inv.getBalance().compareTo(inv.getTotal()) < 0) {
            inv.setStatus(PurchaseInvoiceStatus.PARTIAL);
        }
        supplierBalanceOps.addToPaid(inv.getPartyId(), imputed, true);
        return imputed;
    }

    // ────────────────────────────────────────────────────────────────────────
    // PDF
    // ────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(UUID id) {
        PurchaseInvoice inv = purchaseInvoices.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", id));
        PartnerSummary supplier = supplierLookup.findById(inv.getPartyId()).orElse(null);
        List<PurchaseInvoiceLine> lines = purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(id);
        Map<String, Object> vars = buildPurchaseInvoiceVars(inv, lines, supplier);
        return renderer.renderPdf(PdfRenderRequest.of("purchase-invoice", vars));
    }

    @Transactional(readOnly = true)
    public byte[] generateOrderPdf(UUID id) {
        PurchaseOrder po = purchaseOrders.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_order", id));
        PartnerSummary supplier = supplierLookup.findById(po.getPartyId()).orElse(null);
        List<PurchaseOrderLine> lines = purchaseOrderLines.findByPurchaseOrderIdOrderByLineNumberAsc(id);
        Map<String, Object> vars = buildPurchaseOrderVars(po, lines, supplier);
        return renderer.renderPdf(PdfRenderRequest.of("purchase-order", vars));
    }

    @Transactional(readOnly = true)
    public byte[] generateCreditNotePdf(UUID id) {
        PurchaseCreditNote cn = purchaseCreditNotes.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_credit_note", id));
        PurchaseInvoice inv = purchaseInvoices.findById(cn.getPurchaseInvoiceId()).orElse(null);
        PartnerSummary supplier = supplierLookup.findById(cn.getPartyId()).orElse(null);
        List<PurchaseCreditNoteLine> lines = purchaseCreditNoteLines.findByPurchaseCreditNoteIdOrderByLineNumberAsc(id);
        Map<String, Object> vars = buildCreditNoteVars(cn, lines, supplier, inv);
        return renderer.renderPdf(PdfRenderRequest.of("purchase-credit-note", vars));
    }

    // ────────────────────────────────────────────────────────────────────────
    // Builders / mappers
    // ────────────────────────────────────────────────────────────────────────

    private List<PurchaseOrderLine> buildPoLines(UUID poId, List<PurchaseDto.LineRequest> lineReqs) {
        List<PurchaseOrderLine> result = new ArrayList<>();
        int i = 1;
        for (PurchaseDto.LineRequest lr : lineReqs) {
            ProductSnapshot product = catalog.findProductById(lr.productId())
                    .orElseThrow(() -> NotFoundException.of("entity.product", lr.productId()));
            BigDecimal taxRate = lr.taxRate() != null ? lr.taxRate() : BigDecimal.ZERO;
            BigDecimal lineTotal = lr.unitCost().multiply(lr.quantity()).setScale(2, RoundingMode.HALF_UP);
            PurchaseOrderLine line = PurchaseOrderLine.builder()
                    .purchaseOrderId(poId).lineNumber(i++)
                    .productId(lr.productId())
                    .uomId(lr.uomId() != null ? lr.uomId() : product.baseUomId())
                    .quantity(lr.quantity()).unitCost(lr.unitCost())
                    .taxRate(taxRate).lineTotal(lineTotal)
                    .snapshotName(product.name()).snapshotSku(product.sku())
                    .build();
            purchaseOrderLines.save(line);
            result.add(line);
        }
        return result;
    }

    private List<PurchaseInvoiceLine> buildInvoiceLines(UUID invoiceId, List<PurchaseDto.LineRequest> lineReqs) {
        List<PurchaseInvoiceLine> result = new ArrayList<>();
        int i = 1;
        for (PurchaseDto.LineRequest lr : lineReqs) {
            ProductSnapshot product = catalog.findProductById(lr.productId())
                    .orElseThrow(() -> NotFoundException.of("entity.product", lr.productId()));
            BigDecimal taxRate = lr.taxRate() != null ? lr.taxRate() : BigDecimal.ZERO;
            BigDecimal lineTotal = lr.unitCost().multiply(lr.quantity()).setScale(2, RoundingMode.HALF_UP);
            PurchaseInvoiceLine line = PurchaseInvoiceLine.builder()
                    .purchaseInvoiceId(invoiceId).lineNumber(i++)
                    .productId(lr.productId())
                    .uomId(lr.uomId() != null ? lr.uomId() : product.baseUomId())
                    .quantity(lr.quantity()).unitCost(lr.unitCost())
                    .taxRate(taxRate).lineTotal(lineTotal)
                    .snapshotName(product.name()).snapshotSku(product.sku())
                    .build();
            purchaseInvoiceLines.save(line);
            result.add(line);
        }
        return result;
    }

    private void computePoTotals(PurchaseOrder po, List<PurchaseOrderLine> lines) {
        BigDecimal sub = lines.stream().map(PurchaseOrderLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = lines.stream()
                .map(l -> l.getLineTotal().multiply(l.getTaxRate()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        po.setSubtotal(sub);
        po.setTaxAmount(tax);
        po.setTotal(sub.add(tax));
    }

    private void computeInvoiceTotals(PurchaseInvoice inv, List<PurchaseInvoiceLine> lines) {
        BigDecimal sub = lines.stream().map(PurchaseInvoiceLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = lines.stream()
                .map(l -> l.getLineTotal().multiply(l.getTaxRate()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        inv.setSubtotal(sub);
        inv.setTaxAmount(tax);
        inv.setTotal(sub.add(tax));
        inv.setBalance(inv.getTotal());
    }

    private Map<UUID, Long> creditNoteCounts(List<UUID> invoiceIds) {
        Map<UUID, Long> map = new HashMap<>();
        if (invoiceIds.isEmpty()) return map;
        for (Object[] row : purchaseCreditNotes.countNonDraftByInvoiceIds(invoiceIds)) {
            map.put((UUID) row[0], (Long) row[1]);
        }
        return map;
    }

    private Map<UUID, BigDecimal> aggregate(List<Object[]> rows) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Object[] row : rows) map.put((UUID) row[0], (BigDecimal) row[1]);
        return map;
    }

    private PurchaseDto.LineDto toLineDto(PurchaseOrderLine l) {
        return new PurchaseDto.LineDto(l.getId(), l.getLineNumber(), l.getProductId(), l.getUomId(),
                l.getQuantity(), l.getUnitCost(), l.getTaxRate(),
                l.getLineTotal(), l.getSnapshotName(), l.getSnapshotSku());
    }

    private PurchaseDto.LineDto toLineDto(PurchaseInvoiceLine l) {
        return new PurchaseDto.LineDto(l.getId(), l.getLineNumber(), l.getProductId(), l.getUomId(),
                l.getQuantity(), l.getUnitCost(), l.getTaxRate(),
                l.getLineTotal(), l.getSnapshotName(), l.getSnapshotSku());
    }

    private PurchaseDto.PurchaseOrderDto toPoDto(PurchaseOrder po, List<PurchaseOrderLine> lines, String supplierName) {
        return new PurchaseDto.PurchaseOrderDto(po.getId(), po.getNumber(),
                po.getPartyId(), supplierName, po.getWarehouseId(),
                po.getOrderDate(), po.getExpectedDate(), po.getStatus().name(),
                po.getCurrency(), po.getSubtotal(), po.getTaxAmount(), po.getTotal(),
                po.getNotes(), po.getConvertedToInvoiceId(),
                lines.stream().map(this::toLineDto).toList(),
                po.getCreatedAt());
    }

    private PurchaseDto.PurchaseInvoiceDto toInvoiceDto(PurchaseInvoice inv, List<PurchaseInvoiceLine> lines, String supplierName) {
        return toInvoiceDto(inv, lines, supplierName, purchaseCreditNotes.countNonDraftByInvoiceId(inv.getId()));
    }

    private PurchaseDto.PurchaseInvoiceDto toInvoiceDto(PurchaseInvoice inv, List<PurchaseInvoiceLine> lines,
                                                        String supplierName, long creditNoteCount) {
        return new PurchaseDto.PurchaseInvoiceDto(inv.getId(), inv.getNumber(),
                inv.getPartyId(), supplierName, inv.getPurchaseOrderId(),
                inv.getSupplierReference(), inv.getInvoiceDate(), inv.getDueDate(),
                inv.getStatus().name(), inv.getReceptionStatus().name(), inv.getCurrency(),
                inv.getSubtotal(), inv.getTaxAmount(), inv.getTotal(),
                inv.getPaidAmount(), inv.getBalance(), inv.getNotes(),
                lines.stream().map(this::toLineDto).toList(), creditNoteCount, inv.getCreatedAt());
    }

    private PurchaseDto.PurchaseCreditNoteDto toCreditNoteDto(PurchaseCreditNote cn) {
        String supplierName = supplierLookup.findById(cn.getPartyId()).map(PartnerSummary::name).orElse("");
        List<PurchaseDto.PurchaseCreditNoteLineDto> lineDtos = purchaseCreditNoteLines
                .findByPurchaseCreditNoteIdOrderByLineNumberAsc(cn.getId()).stream()
                .map(l -> new PurchaseDto.PurchaseCreditNoteLineDto(l.getId(), l.getLineNumber(),
                        l.getProductId(), l.getUomId(), l.getQuantity(), l.getUnitCost(),
                        l.getTaxRate(), l.getLineTotal(), l.getReturnedToStockQty(),
                        l.getSnapshotName(), l.getSnapshotSku()))
                .toList();
        return new PurchaseDto.PurchaseCreditNoteDto(cn.getId(), cn.getNumber(),
                cn.getPurchaseInvoiceId(), cn.getPartyId(), supplierName,
                cn.getIssueDate(), cn.getReason(),
                cn.getSubtotal(), cn.getTaxAmount(), cn.getTotal(), cn.getAmount(),
                cn.getStatus().name(), cn.getCurrency(), lineDtos, cn.getCreatedAt());
    }

    private PurchaseInvoiceSummary toInvoiceSummary(PurchaseInvoice inv) {
        return new PurchaseInvoiceSummary(inv.getId(), inv.getNumber(),
                inv.getPartyId(), inv.getDueDate(), inv.getTotal(),
                inv.getPaidAmount(), inv.getBalance(), inv.getStatus().name());
    }

    private Map<String, Object> buildPurchaseInvoiceVars(PurchaseInvoice inv, List<PurchaseInvoiceLine> lines, PartnerSummary supplier) {
        String payStatus = switch (inv.getStatus()) {
            case PAID -> "PAYÉE";
            case PARTIAL -> "PARTIELLE";
            default -> "NON PAYÉE";
        };
        record LineModel(String productName, String sku, BigDecimal quantity, BigDecimal unitCost,
                         BigDecimal taxRate, BigDecimal lineTotal) {}
        record InvoiceModel(String number, String supplierReference, LocalDate invoiceDate, LocalDate dueDate,
                            String paymentStatus, String paymentStatusLabel,
                            BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total,
                            BigDecimal paidAmount, BigDecimal balance,
                            String currency, String notes, List<LineModel> lines) {}
        record SupplierModel(String code, String name, String phone, String email) {}
        var linesModel = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantity(), l.getUnitCost(), l.getTaxRate(), l.getLineTotal())).toList();
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("invoice", new InvoiceModel(inv.getNumber(), inv.getSupplierReference(),
                inv.getInvoiceDate(), inv.getDueDate(),
                inv.getStatus().name().toLowerCase(), payStatus,
                inv.getSubtotal(), inv.getTaxAmount(), inv.getTotal(),
                inv.getPaidAmount(), inv.getBalance(),
                inv.getCurrency(), inv.getNotes(), linesModel));
        vars.put("supplier", new SupplierModel(
                supplier != null ? supplier.code() : "",
                supplier != null ? supplier.name() : "",
                supplier != null ? supplier.phone() : "",
                supplier != null ? supplier.email() : ""));
        return vars;
    }

    private Map<String, Object> buildCreditNoteVars(PurchaseCreditNote cn, List<PurchaseCreditNoteLine> lines,
                                                    PartnerSummary supplier, PurchaseInvoice inv) {
        record LineModel(String productName, String sku, BigDecimal quantity, BigDecimal unitCost,
                         BigDecimal taxRate, BigDecimal lineTotal) {}
        record CreditNoteModel(String number, LocalDate issueDate, String reason, String invoiceNumber,
                               BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total,
                               String currency, List<LineModel> lines) {}
        record SupplierModel(String code, String name, String phone, String email) {}
        var lm = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantity(), l.getUnitCost(), l.getTaxRate(), l.getLineTotal())).toList();
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("creditNote", new CreditNoteModel(cn.getNumber(), cn.getIssueDate(), cn.getReason(),
                inv != null ? inv.getNumber() : "",
                cn.getSubtotal(), cn.getTaxAmount(), cn.getTotal(), cn.getCurrency(), lm));
        vars.put("supplier", new SupplierModel(
                supplier != null ? supplier.code() : "",
                supplier != null ? supplier.name() : "",
                supplier != null ? supplier.phone() : "",
                supplier != null ? supplier.email() : ""));
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

    private Map<String, Object> buildPurchaseOrderVars(PurchaseOrder po, List<PurchaseOrderLine> lines, PartnerSummary supplier) {
        record LineModel(String productName, String sku, BigDecimal quantity,
                         BigDecimal unitCost, BigDecimal taxRate, BigDecimal lineTotal) {}
        record OrderModel(String number, LocalDate orderDate, LocalDate expectedDate, String statusLabel,
                          BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total,
                          String currency, String notes, List<LineModel> lines) {}
        record SupplierModel(String code, String name, String phone, String email) {}
        var lm = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantity(), l.getUnitCost(), l.getTaxRate(), l.getLineTotal())).toList();
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("order", new OrderModel(po.getNumber(), po.getOrderDate(), po.getExpectedDate(),
                po.getStatus().name(),
                po.getSubtotal(), po.getTaxAmount(), po.getTotal(),
                po.getCurrency(), po.getNotes(), lm));
        vars.put("supplier", new SupplierModel(
                supplier != null ? supplier.code() : "",
                supplier != null ? supplier.name() : "",
                supplier != null ? supplier.phone() : "",
                supplier != null ? supplier.email() : ""));
        return vars;
    }
}
