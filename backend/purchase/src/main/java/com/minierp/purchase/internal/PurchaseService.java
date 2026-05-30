package com.minierp.purchase.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.catalog.api.ProductSnapshot;
import com.minierp.partner.api.ApBalanceOperations;
import com.minierp.partner.api.PartnerLookup;
import com.minierp.partner.api.PartnerSummary;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.inventory.api.StockMovementDto;
import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.lotexpiry.api.LotOperations;
import com.minierp.purchase.api.PurchaseDto;
import com.minierp.purchase.api.PurchaseInvoiceOperations;
import com.minierp.purchase.api.PurchaseInvoiceSummary;
import com.minierp.sales.api.NumberingOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.shared.util.PageResponse;
import com.minierp.tenant.api.TenantLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final PartnerLookup supplierLookup;
    private final ApBalanceOperations supplierBalanceOps;
    private final CatalogLookup catalog;
    private final StockOperations stockOps;
    private final LotOperations lotOps;
    private final NumberingOperations numbering;
    private final DocumentRenderer renderer;
    private final TenantLookup tenantLookup;

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
        if (po.getStatus() == PurchaseOrderStatus.RECEIVED
                || po.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException("error.purchase.po_already_received",
                    Map.of("status", po.getStatus().name()));
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        String name = supplierLookup.findById(po.getPartyId()).map(PartnerSummary::name).orElse("");
        return toPoDto(po, purchaseOrderLines.findByPurchaseOrderIdOrderByLineNumberAsc(id), name);
    }

    /**
     * Receive part (or all) of a confirmed purchase order. For each received line:
     *   - validates quantityReceived ≤ ordered − previously received,
     *   - if the product has trackExpiry, requires lotNumber + expirationDate and creates a ProductLot,
     *   - posts a PURCHASE_RECEIPT StockMovement (recomputes CMP),
     *   - bumps PurchaseOrderLine.quantityReceived.
     * After all lines are processed, updates PO.status to PARTIALLY_RECEIVED or RECEIVED.
     */
    @Transactional
    public PurchaseDto.ReceiptResult receive(UUID purchaseOrderId, PurchaseDto.ReceivePurchaseOrderRequest req) {
        PurchaseOrder po = purchaseOrders.lockById(purchaseOrderId)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_order", purchaseOrderId));
        if (po.getStatus() != PurchaseOrderStatus.CONFIRMED
                && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException("error.purchase.po_not_receivable",
                    Map.of("status", po.getStatus().name()));
        }

        UUID warehouseId = req.warehouseId() != null ? req.warehouseId() : po.getWarehouseId();
        UUID userId = CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
        List<PurchaseDto.ReceiptLineResult> results = new ArrayList<>();

        for (PurchaseDto.ReceiveLineRequest lineReq : req.lines()) {
            PurchaseOrderLine line = purchaseOrderLines.lockById(lineReq.purchaseOrderLineId())
                    .orElseThrow(() -> NotFoundException.of("entity.purchase_order_line", lineReq.purchaseOrderLineId()));
            if (!line.getPurchaseOrderId().equals(purchaseOrderId)) {
                throw new BusinessException("error.purchase.line_not_in_po",
                        Map.of("lineId", line.getId()));
            }

            BigDecimal remaining = line.getQuantity().subtract(line.getQuantityReceived());
            if (lineReq.quantityReceived().compareTo(remaining) > 0) {
                throw new BusinessException("error.purchase.over_receipt",
                        Map.of("requested", lineReq.quantityReceived(), "remaining", remaining));
            }

            ProductSnapshot product = catalog.findProductById(line.getProductId())
                    .orElseThrow(() -> NotFoundException.of("entity.product", line.getProductId()));

            UUID lotId = null;
            if (product.trackExpiry()) {
                if (lineReq.lotNumber() == null || lineReq.lotNumber().isBlank()
                        || lineReq.expirationDate() == null) {
                    throw new BusinessException("error.purchase.lot_data_required",
                            Map.of("productId", product.id()));
                }
                lotId = lotOps.receiveLot(
                        product.id(), warehouseId, product.baseUomId(),
                        lineReq.lotNumber(), lineReq.expirationDate(), lineReq.productionDate(),
                        lineReq.quantityReceived(), line.getUnitCost(),
                        po.getPartyId(), po.getId());
            }

            StockMovementDto mv = stockOps.receive(
                    warehouseId, product.id(), lineReq.quantityReceived(), line.getUnitCost(),
                    StockMovementType.PURCHASE_RECEIPT,
                    "PURCHASE_ORDER", po.getId(), po.getNumber(),
                    "PO " + po.getNumber() + " line " + line.getLineNumber(),
                    userId);

            line.setQuantityReceived(line.getQuantityReceived().add(lineReq.quantityReceived()));
            results.add(new PurchaseDto.ReceiptLineResult(line.getId(), lineReq.quantityReceived(), mv.id(), lotId));
        }

        // Re-evaluate PO status across all lines.
        List<PurchaseOrderLine> allLines = purchaseOrderLines.findByPurchaseOrderIdOrderByLineNumberAsc(po.getId());
        boolean allFullyReceived = allLines.stream()
                .allMatch(l -> l.getQuantityReceived().compareTo(l.getQuantity()) >= 0);
        boolean anyReceived = allLines.stream()
                .anyMatch(l -> l.getQuantityReceived().compareTo(BigDecimal.ZERO) > 0);
        if (allFullyReceived) {
            po.setStatus(PurchaseOrderStatus.RECEIVED);
        } else if (anyReceived) {
            po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }

        return new PurchaseDto.ReceiptResult(po.getId(), po.getStatus().name(), results);
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
                .currency(req.currency() != null ? req.currency() : supplier.currency())
                .notes(req.notes())
                .build();
        purchaseInvoices.save(inv);

        List<PurchaseInvoiceLine> built = buildInvoiceLines(inv.getId(), req.lines());
        computeInvoiceTotals(inv, built);

        supplierBalanceOps.addToInvoiced(inv.getPartyId(), inv.getTotal());
        return toInvoiceDto(inv, built, supplier.name());
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
        return PageResponse.of(page.map(inv -> {
            String name = supplierLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
            return toInvoiceDto(inv, purchaseInvoiceLines.findByPurchaseInvoiceIdOrderByLineNumberAsc(inv.getId()), name);
        }));
    }

    @Transactional
    public PurchaseDto.PurchaseInvoiceDto cancelInvoice(UUID id) {
        PurchaseInvoice inv = purchaseInvoices.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.purchase_invoice", id));
        if (inv.getStatus() == PurchaseInvoiceStatus.PAID
                || inv.getStatus() == PurchaseInvoiceStatus.PARTIAL) {
            throw new BusinessException("error.purchase.invoice_already_paid",
                    Map.of("status", inv.getStatus().name()));
        }
        if (inv.getStatus() != PurchaseInvoiceStatus.CANCELLED) {
            supplierBalanceOps.addToInvoiced(inv.getPartyId(), inv.getTotal().negate());
            inv.setStatus(PurchaseInvoiceStatus.CANCELLED);
        }
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

    private PurchaseDto.LineDto toLineDto(PurchaseOrderLine l) {
        return new PurchaseDto.LineDto(l.getId(), l.getLineNumber(), l.getProductId(), l.getUomId(),
                l.getQuantity(), l.getQuantityReceived(), l.getUnitCost(), l.getTaxRate(),
                l.getLineTotal(), l.getSnapshotName(), l.getSnapshotSku());
    }

    private PurchaseDto.LineDto toLineDto(PurchaseInvoiceLine l) {
        return new PurchaseDto.LineDto(l.getId(), l.getLineNumber(), l.getProductId(), l.getUomId(),
                l.getQuantity(), BigDecimal.ZERO, l.getUnitCost(), l.getTaxRate(),
                l.getLineTotal(), l.getSnapshotName(), l.getSnapshotSku());
    }

    private PurchaseDto.PurchaseOrderDto toPoDto(PurchaseOrder po, List<PurchaseOrderLine> lines, String supplierName) {
        return new PurchaseDto.PurchaseOrderDto(po.getId(), po.getNumber(),
                po.getPartyId(), supplierName, po.getWarehouseId(),
                po.getOrderDate(), po.getExpectedDate(), po.getStatus().name(),
                po.getCurrency(), po.getSubtotal(), po.getTaxAmount(), po.getTotal(),
                po.getNotes(), lines.stream().map(this::toLineDto).toList(),
                po.getCreatedAt());
    }

    private PurchaseDto.PurchaseInvoiceDto toInvoiceDto(PurchaseInvoice inv, List<PurchaseInvoiceLine> lines, String supplierName) {
        return new PurchaseDto.PurchaseInvoiceDto(inv.getId(), inv.getNumber(),
                inv.getPartyId(), supplierName, inv.getPurchaseOrderId(),
                inv.getSupplierReference(), inv.getInvoiceDate(), inv.getDueDate(),
                inv.getStatus().name(), inv.getCurrency(),
                inv.getSubtotal(), inv.getTaxAmount(), inv.getTotal(),
                inv.getPaidAmount(), inv.getBalance(), inv.getNotes(),
                lines.stream().map(this::toLineDto).toList(), inv.getCreatedAt());
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
        record LineModel(String productName, String sku, BigDecimal quantity, BigDecimal quantityReceived,
                         BigDecimal unitCost, BigDecimal taxRate, BigDecimal lineTotal) {}
        record OrderModel(String number, LocalDate orderDate, LocalDate expectedDate, String statusLabel,
                          BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total,
                          String currency, String notes, List<LineModel> lines) {}
        record SupplierModel(String code, String name, String phone, String email) {}
        var lm = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantity(), l.getQuantityReceived(), l.getUnitCost(), l.getTaxRate(), l.getLineTotal())).toList();
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
