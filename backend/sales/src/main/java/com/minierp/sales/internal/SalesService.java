package com.minierp.sales.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.partner.api.ArBalanceOperations;
import com.minierp.partner.api.PartnerLookup;
import com.minierp.partner.api.PartnerSummary;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.sales.api.InvoiceOperations;
import com.minierp.sales.api.InvoiceSummary;
import com.minierp.sales.api.OrderOperations;
import com.minierp.sales.api.SalesDto;
import com.minierp.sales.api.SalesStatementLookup;
import com.minierp.sales.api.StatementCreditNoteEntry;
import com.minierp.sales.api.StatementInvoiceEntry;
import com.minierp.sales.api.StatementInvoiceLine;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.shared.util.PageResponse;
import com.minierp.tenant.api.TenantLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.partner.api.CustomerCreditOperations;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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
public class SalesService implements InvoiceOperations, SalesStatementLookup, OrderOperations {

    private final QuoteRepository quotes;
    private final QuoteLineRepository quoteLines;
    private final OrderRepository orders;
    private final OrderLineRepository orderLines;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository invoiceLines;
    private final CreditNoteRepository creditNotes;
    private final CreditNoteLineRepository creditNoteLines;
    private final NumberingService numbering;
    private final CatalogLookup catalog;
    private final PartnerLookup customerLookup;
    private final ArBalanceOperations balanceOps;
    private final CustomerCreditOperations customerCreditOps;
    private final StockOperations stockOps;
    private final JdbcTemplate jdbc;
    private final DocumentRenderer renderer;
    private final TenantLookup tenantLookup;

    // ── SalesStatementLookup (used by the customer-statement aggregator) ────

    @Override
    @Transactional(readOnly = true)
    public List<StatementInvoiceEntry> findInvoicesForStatement(
            UUID customerId, LocalDate from, LocalDate to, boolean detailed) {
        return invoices.findForStatement(customerId, from, to).stream()
                .map(inv -> new StatementInvoiceEntry(
                        inv.getId(), inv.getNumber(),
                        inv.getIssueDate(), inv.getDueDate(),
                        inv.getTotal(), inv.getPaidAmount(), inv.getBalance(),
                        inv.getStatus().name(),
                        detailed ? toStatementLines(inv.getId()) : null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatementCreditNoteEntry> findCreditNotesForStatement(
            UUID customerId, LocalDate from, LocalDate to) {
        return creditNotes.findForStatement(customerId, from, to).stream()
                .map(cn -> new StatementCreditNoteEntry(
                        cn.getId(), cn.getNumber(), cn.getIssueDate(),
                        cn.getAmount(), cn.getReason(), cn.getStatus().name()))
                .toList();
    }

    private List<StatementInvoiceLine> toStatementLines(UUID invoiceId) {
        return invoiceLines.findByInvoiceIdOrderByLineNumberAsc(invoiceId).stream()
                .map(l -> new StatementInvoiceLine(
                        l.getSnapshotName(), l.getSnapshotSku(),
                        l.getQuantity(), l.getUnitPrice(),
                        l.getDiscountPercent(), l.getLineTotal()))
                .toList();
    }

    // ── InvoiceOperations (used by payment module) ───────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<InvoiceSummary> findById(UUID id) {
        return invoices.findById(id).map(this::toInvoiceSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<InvoiceSummary> findByOrderId(UUID orderId) {
        return invoices.findActiveByOrderId(orderId).stream()
                .findFirst()
                .map(this::toInvoiceSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceSummary> findUnpaidByCustomer(UUID customerId) {
        return invoices.findUnpaidByPartyOrderByDueDate(customerId)
                .stream().map(this::toInvoiceSummary).toList();
    }

    @Override
    @Transactional
    public void applyPayment(UUID invoiceId, BigDecimal amount) {
        Invoice inv = invoices.lockById(invoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.invoice", invoiceId));
        inv.setPaidAmount(inv.getPaidAmount().add(amount));
        inv.setBalance(inv.getTotal().subtract(inv.getPaidAmount()).max(BigDecimal.ZERO));
        if (inv.getBalance().compareTo(BigDecimal.ZERO) == 0) {
            inv.setStatus(InvoiceStatus.PAID);
        } else if (inv.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            inv.setStatus(InvoiceStatus.PARTIAL);
        }
        balanceOps.addToPaid(inv.getPartyId(), amount, true);
    }

    @Override
    @Transactional
    public BigDecimal applyCredit(UUID invoiceId, BigDecimal amount) {
        Invoice inv = invoices.lockById(invoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.invoice", invoiceId));
        BigDecimal imputed = amount.min(inv.getBalance()).max(BigDecimal.ZERO);
        if (imputed.signum() == 0) return BigDecimal.ZERO;
        inv.setBalance(inv.getBalance().subtract(imputed));
        if (inv.getBalance().compareTo(BigDecimal.ZERO) == 0) {
            inv.setStatus(InvoiceStatus.PAID);
        } else if (inv.getBalance().compareTo(inv.getTotal()) < 0) {
            inv.setStatus(InvoiceStatus.PARTIAL);
        }
        balanceOps.addToPaid(inv.getPartyId(), imputed, true);
        return imputed;
    }

    @Override
    @Transactional
    public void markOverdue(UUID invoiceId) {
        invoices.findById(invoiceId).ifPresent(inv -> {
            if (inv.getStatus() == InvoiceStatus.ISSUED || inv.getStatus() == InvoiceStatus.PARTIAL) {
                inv.setStatus(InvoiceStatus.OVERDUE);
                balanceOps.addToOverdue(inv.getPartyId(), inv.getBalance());
            }
        });
    }

    // ── Quotes ───────────────────────────────────────────────────────────────

    @Transactional
    public SalesDto.QuoteDto createQuote(SalesDto.CreateQuoteRequest req) {
        PartnerSummary customer = customerLookup.findById(req.customerId())
                .orElseThrow(() -> NotFoundException.of("entity.customer", req.customerId()));
        String number = numbering.next(DocumentType.QUOTE);
        Quote quote = Quote.builder()
                .number(number)
                .partyId(req.customerId())
                .issueDate(req.issueDate() != null ? req.issueDate() : LocalDate.now())
                .validUntil(req.validUntil())
                .currency(req.currency() != null ? req.currency() : customer.currency())
                .notes(req.notes())
                .build();
        quotes.save(quote);
        List<QuoteLine> built = buildQuoteLines(quote.getId(), req.lines(), quote.getCurrency());
        computeQuoteTotals(quote, built);
        return toQuoteDto(quote, built, customer.name());
    }

    @Transactional(readOnly = true)
    public SalesDto.QuoteDto getQuote(UUID id) {
        Quote q = quotes.findById(id).orElseThrow(() -> NotFoundException.of("entity.quote", id));
        String customerName = customerLookup.findById(q.getPartyId()).map(PartnerSummary::name).orElse("");
        return toQuoteDto(q, quoteLines.findByQuoteIdOrderByLineNumberAsc(q.getId()), customerName);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesDto.QuoteDto> listQuotes(UUID customerId, Pageable pageable) {
        var page = customerId != null
                ? quotes.findByPartyId(customerId, pageable)
                : quotes.findAll(pageable);
        return PageResponse.of(page.map(q -> {
            String name = customerLookup.findById(q.getPartyId()).map(PartnerSummary::name).orElse("");
            return toQuoteDto(q, quoteLines.findByQuoteIdOrderByLineNumberAsc(q.getId()), name);
        }));
    }

    @Transactional
    public SalesDto.QuoteDto updateQuoteStatus(UUID id, String status) {
        Quote q = quotes.findById(id).orElseThrow(() -> NotFoundException.of("entity.quote", id));
        q.setStatus(QuoteStatus.valueOf(status));
        String name = customerLookup.findById(q.getPartyId()).map(PartnerSummary::name).orElse("");
        return toQuoteDto(q, quoteLines.findByQuoteIdOrderByLineNumberAsc(id), name);
    }

    @Transactional
    public SalesDto.OrderDto convertQuoteToOrder(UUID quoteId, boolean deliveryRequired) {
        Quote q = quotes.findById(quoteId).orElseThrow(() -> NotFoundException.of("entity.quote", quoteId));
        if (q.getStatus() != QuoteStatus.DRAFT && q.getStatus() != QuoteStatus.SENT && q.getStatus() != QuoteStatus.ACCEPTED) {
            throw new BusinessException("error.sales.quote_not_convertible", Map.of("status", q.getStatus()));
        }
        String orderNumber = numbering.next(DocumentType.ORDER);
        Order order = Order.builder()
                .number(orderNumber)
                .partyId(q.getPartyId())
                .quoteId(quoteId)
                .orderDate(LocalDate.now())
                .deliveryRequired(deliveryRequired)
                .currency(q.getCurrency())
                .subtotal(q.getSubtotal())
                .discountAmount(q.getDiscountAmount())
                .taxAmount(q.getTaxAmount())
                .total(q.getTotal())
                .notes(q.getNotes())
                .build();
        orders.save(order);

        List<QuoteLine> qLines = quoteLines.findByQuoteIdOrderByLineNumberAsc(quoteId);
        for (QuoteLine ql : qLines) {
            orderLines.save(OrderLine.builder()
                    .orderId(order.getId())
                    .lineNumber(ql.getLineNumber())
                    .productId(ql.getProductId())
                    .uomId(ql.getUomId())
                    .quantity(ql.getQuantity())
                    .unitPrice(ql.getUnitPrice())
                    .discountPercent(ql.getDiscountPercent())
                    .taxRate(ql.getTaxRate())
                    .lineTotal(ql.getLineTotal())
                    .snapshotName(ql.getSnapshotName())
                    .snapshotSku(ql.getSnapshotSku())
                    .build());
        }
        q.setStatus(QuoteStatus.CONVERTED);
        q.setConvertedToOrderId(order.getId());

        String name = customerLookup.findById(order.getPartyId()).map(PartnerSummary::name).orElse("");
        return toOrderDto(order, orderLines.findByOrderIdOrderByLineNumberAsc(order.getId()), name);
    }

    // ── Orders ───────────────────────────────────────────────────────────────

    @Transactional
    public SalesDto.OrderDto createOrder(SalesDto.CreateOrderRequest req) {
        PartnerSummary customer = customerLookup.findById(req.customerId())
                .orElseThrow(() -> NotFoundException.of("entity.customer", req.customerId()));
        String number = numbering.next(DocumentType.ORDER);
        Order order = Order.builder()
                .number(number)
                .partyId(req.customerId())
                .quoteId(req.quoteId())
                .orderDate(req.orderDate() != null ? req.orderDate() : LocalDate.now())
                .deliveryRequired(req.deliveryRequired())
                .currency(req.currency() != null ? req.currency() : customer.currency())
                .notes(req.notes())
                .build();
        orders.save(order);
        List<OrderLine> built = buildOrderLines(order.getId(), req.lines());
        computeOrderTotals(order, built);
        return toOrderDto(order, built, customer.name());
    }

    @Transactional(readOnly = true)
    public SalesDto.OrderDto getOrder(UUID id) {
        Order o = orders.findById(id).orElseThrow(() -> NotFoundException.of("entity.order", id));
        String name = customerLookup.findById(o.getPartyId()).map(PartnerSummary::name).orElse("");
        return toOrderDto(o, orderLines.findByOrderIdOrderByLineNumberAsc(id), name);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesDto.OrderDto> listOrders(UUID customerId, Pageable pageable) {
        var page = customerId != null
                ? orders.findByPartyId(customerId, pageable)
                : orders.findAll(pageable);
        return PageResponse.of(page.map(o -> {
            String name = customerLookup.findById(o.getPartyId()).map(PartnerSummary::name).orElse("");
            return toOrderDto(o, orderLines.findByOrderIdOrderByLineNumberAsc(o.getId()), name);
        }));
    }

    @Transactional
    public SalesDto.OrderDto updateOrderStatus(UUID id, String status) {
        Order o = orders.findById(id).orElseThrow(() -> NotFoundException.of("entity.order", id));
        OrderStatus target = OrderStatus.valueOf(status);
        // Delivery-driven statuses are auto-derived from shipments — reject manual transitions.
        if (target == OrderStatus.PARTIALLY_DELIVERED || target == OrderStatus.DELIVERED) {
            throw new BusinessException("error.order.status_auto_only",
                    Map.of("status", target.name()));
        }
        o.setStatus(target);
        String name = customerLookup.findById(o.getPartyId()).map(PartnerSummary::name).orElse("");
        return toOrderDto(o, orderLines.findByOrderIdOrderByLineNumberAsc(id), name);
    }

    // ── OrderOperations (used by delivery module to auto-derive status) ─────

    @Override
    @Transactional
    public void recomputeDeliveryStatus(UUID orderId, Map<UUID, BigDecimal> totalDeliveredByProduct) {
        Order o = orders.findById(orderId).orElse(null);
        if (o == null) return;
        OrderStatus current = o.getStatus();
        // Lifecycle: DRAFT → CONFIRMED → INVOICED → PARTIALLY_DELIVERED → DELIVERED.
        // Recompute when the order is in any post-confirm state; leave DRAFT/CANCELLED alone.
        if (current != OrderStatus.CONFIRMED
                && current != OrderStatus.INVOICED
                && current != OrderStatus.PARTIALLY_DELIVERED
                && current != OrderStatus.DELIVERED) {
            return;
        }
        Map<UUID, BigDecimal> orderedByProduct = new HashMap<>();
        for (OrderLine ol : orderLines.findByOrderIdOrderByLineNumberAsc(orderId)) {
            orderedByProduct.merge(ol.getProductId(), ol.getQuantity(), BigDecimal::add);
        }
        boolean allCovered = true;
        boolean anyDelivered = false;
        for (Map.Entry<UUID, BigDecimal> e : orderedByProduct.entrySet()) {
            BigDecimal delivered = totalDeliveredByProduct.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (delivered.signum() > 0) anyDelivered = true;
            if (delivered.compareTo(e.getValue()) < 0) allCovered = false;
        }
        OrderStatus next;
        if (allCovered && !orderedByProduct.isEmpty()) {
            next = OrderStatus.DELIVERED;
        } else if (anyDelivered) {
            next = OrderStatus.PARTIALLY_DELIVERED;
        } else {
            // No shipment yet — keep the current pre-delivery status (CONFIRMED or INVOICED).
            next = current;
        }
        if (next != current) {
            o.setStatus(next);
        }
    }

    @Transactional
    public SalesDto.InvoiceDto convertOrderToInvoice(UUID orderId, LocalDate dueDate, String paymentTerms) {
        Order o = orders.findById(orderId).orElseThrow(() -> NotFoundException.of("entity.order", orderId));
        String invNumber = numbering.next(DocumentType.INVOICE);
        Invoice inv = Invoice.builder()
                .number(invNumber)
                .partyId(o.getPartyId())
                .orderId(orderId)
                .issueDate(LocalDate.now())
                .dueDate(dueDate)
                .status(InvoiceStatus.ISSUED)
                .currency(o.getCurrency())
                .subtotal(o.getSubtotal())
                .discountAmount(o.getDiscountAmount())
                .taxAmount(o.getTaxAmount())
                .total(o.getTotal())
                .balance(o.getTotal())
                .paymentTerms(paymentTerms)
                .notes(o.getNotes())
                .build();
        invoices.save(inv);

        List<OrderLine> oLines = orderLines.findByOrderIdOrderByLineNumberAsc(orderId);
        for (OrderLine ol : oLines) {
            invoiceLines.save(InvoiceLine.builder()
                    .invoiceId(inv.getId())
                    .lineNumber(ol.getLineNumber())
                    .productId(ol.getProductId())
                    .uomId(ol.getUomId())
                    .quantity(ol.getQuantity())
                    .unitPrice(ol.getUnitPrice())
                    .discountPercent(ol.getDiscountPercent())
                    .taxRate(ol.getTaxRate())
                    .lineTotal(ol.getLineTotal())
                    .snapshotName(ol.getSnapshotName())
                    .snapshotSku(ol.getSnapshotSku())
                    .build());
        }
        o.setStatus(OrderStatus.INVOICED);
        balanceOps.addToInvoiced(inv.getPartyId(), inv.getTotal());

        String name = customerLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
        return toInvoiceDto(inv, invoiceLines.findByInvoiceIdOrderByLineNumberAsc(inv.getId()), name);
    }

    // ── Invoices ─────────────────────────────────────────────────────────────

    @Transactional
    public SalesDto.InvoiceDto createInvoice(SalesDto.CreateInvoiceRequest req) {
        PartnerSummary customer = customerLookup.findById(req.customerId())
                .orElseThrow(() -> NotFoundException.of("entity.customer", req.customerId()));
        String number = numbering.next(DocumentType.INVOICE);
        Invoice inv = Invoice.builder()
                .number(number)
                .partyId(req.customerId())
                .orderId(req.orderId())
                .issueDate(req.issueDate() != null ? req.issueDate() : LocalDate.now())
                .dueDate(req.dueDate())
                .status(InvoiceStatus.ISSUED)
                .currency(req.currency() != null ? req.currency() : customer.currency())
                .paymentTerms(req.paymentTerms())
                .notes(req.notes())
                .build();
        invoices.save(inv);
        List<InvoiceLine> built = buildInvoiceLines(inv.getId(), req.lines());
        computeInvoiceTotals(inv, built);
        balanceOps.addToInvoiced(inv.getPartyId(), inv.getTotal());
        return toInvoiceDto(inv, built, customer.name());
    }

    @Transactional(readOnly = true)
    public SalesDto.InvoiceDto getInvoice(UUID id) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> NotFoundException.of("entity.invoice", id));
        String name = customerLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
        return toInvoiceDto(inv, invoiceLines.findByInvoiceIdOrderByLineNumberAsc(id), name);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesDto.InvoiceDto> listInvoices(UUID customerId, Pageable pageable) {
        var page = customerId != null
                ? invoices.findByPartyId(customerId, pageable)
                : invoices.findAll(pageable);
        return PageResponse.of(page.map(inv -> {
            String name = customerLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
            return toInvoiceDto(inv, invoiceLines.findByInvoiceIdOrderByLineNumberAsc(inv.getId()), name);
        }));
    }

    // Invoice cancellation has been removed: annulation now flows through a credit
    // note covering the relevant lines. The CANCELLED enum value is kept for legacy
    // rows already in that state.

    // ── Credit notes ─────────────────────────────────────────────────────────

    @Transactional
    public SalesDto.CreditNoteDto createCreditNote(UUID invoiceId, SalesDto.CreateCreditNoteRequest req) {
        Invoice inv = invoices.findById(invoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.invoice", invoiceId));
        if (inv.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("error.creditnote.invoice_cancelled",
                    Map.of("invoiceId", inv.getId(), "invoiceNumber", inv.getNumber()));
        }
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new BusinessException("error.creditnote.no_lines", Map.of());
        }

        List<InvoiceLine> invLines = invoiceLines.findByInvoiceIdOrderByLineNumberAsc(invoiceId);
        Map<UUID, InvoiceLine> invLineById = invLines.stream()
                .collect(java.util.stream.Collectors.toMap(InvoiceLine::getId, l -> l));

        Map<UUID, BigDecimal> alreadyCreditedByLine = aggregateAlreadyCreditedByLine(invoiceId);
        Map<UUID, BigDecimal> alreadyReturnedByProduct = aggregateAlreadyReturnedByProduct(invoiceId);
        Map<UUID, BigDecimal> deliveredByProduct = inv.getOrderId() == null
                ? Map.of()
                : aggregateDeliveredByProduct(inv.getOrderId());

        // Guard #2 — invoice fully credited already (every line max_creditable == 0)
        boolean anyRoom = invLines.stream().anyMatch(l ->
                l.getQuantity()
                        .subtract(alreadyCreditedByLine.getOrDefault(l.getId(), BigDecimal.ZERO))
                        .signum() > 0);
        if (!anyRoom) {
            throw new BusinessException("error.creditnote.invoice_fully_credited",
                    Map.of("invoiceId", inv.getId(), "invoiceNumber", inv.getNumber()));
        }

        String number = numbering.next(DocumentType.CREDIT_NOTE);
        CreditNote cn = CreditNote.builder()
                .number(number)
                .invoiceId(invoiceId)
                .partyId(inv.getPartyId())
                .issueDate(LocalDate.now())
                .reason(req.reason())
                .currency(inv.getCurrency())
                .status(CreditNoteStatus.ISSUED)
                .build();
        creditNotes.save(cn);

        UUID warehouseId = inv.getOrderId() == null ? null : resolveReturnWarehouseId(inv.getOrderId());

        // Track returns per product (line-level returnedToStockQty); seed with already-returned values.
        Map<UUID, BigDecimal> returnedThisCn = new HashMap<>();
        BigDecimal subtotalHt = BigDecimal.ZERO;
        int lineNo = 1;
        for (SalesDto.CreateCreditNoteLine lr : req.lines()) {
            if (lr.quantity() == null || lr.quantity().signum() <= 0) continue;
            InvoiceLine il = invLineById.get(lr.invoiceLineId());
            if (il == null) {
                throw new BusinessException("error.creditnote.line_not_found",
                        Map.of("invoiceLineId", lr.invoiceLineId()));
            }
            BigDecimal alreadyCredited = alreadyCreditedByLine.getOrDefault(il.getId(), BigDecimal.ZERO);
            BigDecimal maxCreditable = il.getQuantity().subtract(alreadyCredited);
            if (lr.quantity().compareTo(maxCreditable) > 0) {
                throw new BusinessException("error.creditnote.line_exceeds_invoiced",
                        Map.of("invoiceLineId", il.getId(),
                                "requested", lr.quantity(), "max", maxCreditable));
            }

            BigDecimal discFactor = BigDecimal.ONE.subtract(
                    il.getDiscountPercent().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            BigDecimal lineTotal = il.getUnitPrice().multiply(lr.quantity())
                    .multiply(discFactor).setScale(2, RoundingMode.HALF_UP);

            // Compute stock-return portion: bounded by (delivered - already-returned for this product).
            BigDecimal alreadyReturned = alreadyReturnedByProduct.getOrDefault(il.getProductId(), BigDecimal.ZERO)
                    .add(returnedThisCn.getOrDefault(il.getProductId(), BigDecimal.ZERO));
            BigDecimal delivered = deliveredByProduct.getOrDefault(il.getProductId(), BigDecimal.ZERO);
            BigDecimal remainingDeliveredCap = delivered.subtract(alreadyReturned).max(BigDecimal.ZERO);
            BigDecimal stockReturnQty = lr.quantity().min(remainingDeliveredCap);

            if (stockReturnQty.signum() > 0) {
                if (warehouseId == null) {
                    throw new BusinessException("error.creditnote.warehouse_missing",
                            Map.of("creditNoteId", cn.getId()));
                }
                stockOps.receive(warehouseId, il.getProductId(), stockReturnQty,
                        il.getUnitPrice(), StockMovementType.SALE_RETURN,
                        "CREDIT_NOTE", cn.getId(), cn.getNumber(),
                        "Credit note " + cn.getNumber(), null);
                returnedThisCn.merge(il.getProductId(), stockReturnQty, BigDecimal::add);
            }

            CreditNoteLine cnl = CreditNoteLine.builder()
                    .creditNoteId(cn.getId())
                    .lineNumber(lineNo++)
                    .invoiceLineId(il.getId())
                    .productId(il.getProductId())
                    .uomId(il.getUomId())
                    .quantity(lr.quantity())
                    .unitPrice(il.getUnitPrice())
                    .discountPercent(il.getDiscountPercent())
                    .taxRate(il.getTaxRate())
                    .lineTotal(lineTotal)
                    .returnedToStockQty(stockReturnQty)
                    .snapshotName(il.getSnapshotName())
                    .snapshotSku(il.getSnapshotSku())
                    .build();
            creditNoteLines.save(cnl);
            subtotalHt = subtotalHt.add(lineTotal);
        }

        // Tax allocation: proportional to the original invoice's tax-on-subtotal ratio.
        BigDecimal taxAmount = BigDecimal.ZERO;
        if (inv.getSubtotal().signum() > 0 && inv.getTaxAmount().signum() > 0) {
            taxAmount = subtotalHt
                    .multiply(inv.getTaxAmount())
                    .divide(inv.getSubtotal(), 2, RoundingMode.HALF_UP);
        }
        BigDecimal total = subtotalHt.add(taxAmount);
        cn.setSubtotal(subtotalHt);
        cn.setTaxAmount(taxAmount);
        cn.setTotal(total);
        cn.setAmount(total); // legacy column mirrors total

        // Apply to invoice balance, surplus → customer credit.
        BigDecimal imputed = applyCredit(inv.getId(), total);
        BigDecimal surplus = total.subtract(imputed);
        if (surplus.signum() > 0) {
            customerCreditOps.grantCredit(inv.getPartyId(), surplus, "REFUND",
                    "Credit note " + cn.getNumber());
        }
        cn.setAppliedToInvoiceId(inv.getId());
        cn.setStatus(CreditNoteStatus.APPLIED);

        return toCreditNoteDto(cn);
    }

    private Map<UUID, BigDecimal> aggregateAlreadyCreditedByLine(UUID invoiceId) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Object[] row : creditNoteLines.sumQuantityByInvoiceLine(invoiceId)) {
            map.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return map;
    }

    private Map<UUID, BigDecimal> aggregateAlreadyReturnedByProduct(UUID invoiceId) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Object[] row : creditNoteLines.sumReturnedByProduct(invoiceId)) {
            map.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return map;
    }

    private Map<UUID, BigDecimal> aggregateDeliveredByProduct(UUID orderId) {
        // Cross-module read: delivered quantities live in the delivery module's tables.
        // A direct JDBC read keeps sales free of a compile-time dep on delivery.
        UUID tenant = TenantContext.require();
        Map<UUID, BigDecimal> map = new HashMap<>();
        jdbc.query("""
                SELECT dl.product_id, COALESCE(SUM(dl.quantity_delivered), 0)
                FROM delivery_lines dl
                JOIN deliveries d ON d.id = dl.delivery_id AND d.tenant_id = dl.tenant_id
                WHERE d.tenant_id = ? AND d.order_id = ? AND d.status <> 'CANCELLED'
                GROUP BY dl.product_id
                """, rs -> {
            map.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
        }, tenant, orderId);
        return map;
    }

    private UUID resolveReturnWarehouseId(UUID orderId) {
        UUID tenant = TenantContext.require();
        List<UUID> ids = jdbc.query("""
                SELECT d.warehouse_id FROM deliveries d
                WHERE d.tenant_id = ? AND d.order_id = ? AND d.status <> 'CANCELLED'
                  AND d.warehouse_id IS NOT NULL
                ORDER BY d.created_at ASC LIMIT 1
                """, (rs, i) -> rs.getObject(1, UUID.class), tenant, orderId);
        if (!ids.isEmpty()) return ids.get(0);
        return stockOps.findDefaultWarehouseId().orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesDto.CreditNoteDto> listCreditNotes(UUID customerId, Pageable pageable) {
        var page = customerId != null
                ? creditNotes.findByPartyId(customerId, pageable)
                : creditNotes.findAll(pageable);
        return PageResponse.of(page.map(this::toCreditNoteDto));
    }

    @Transactional(readOnly = true)
    public SalesDto.CreditNoteDto getCreditNote(UUID id) {
        CreditNote cn = creditNotes.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.credit_note", id));
        return toCreditNoteDto(cn);
    }

    @Transactional(readOnly = true)
    public SalesDto.CreditableInvoiceDto getCreditableInvoice(UUID invoiceId) {
        Invoice inv = invoices.findById(invoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.invoice", invoiceId));
        List<InvoiceLine> lines = invoiceLines.findByInvoiceIdOrderByLineNumberAsc(invoiceId);
        Map<UUID, BigDecimal> alreadyCredited = aggregateAlreadyCreditedByLine(invoiceId);
        String customerName = customerLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
        List<SalesDto.CreditableLineDto> lineDtos = lines.stream().map(l -> {
            BigDecimal already = alreadyCredited.getOrDefault(l.getId(), BigDecimal.ZERO);
            BigDecimal max = l.getQuantity().subtract(already).max(BigDecimal.ZERO);
            return new SalesDto.CreditableLineDto(
                    l.getId(), l.getProductId(),
                    l.getSnapshotName(), l.getSnapshotSku(), l.getUomId(),
                    l.getQuantity(), already, max,
                    l.getUnitPrice(), l.getDiscountPercent(), l.getTaxRate());
        }).toList();
        return new SalesDto.CreditableInvoiceDto(
                inv.getId(), inv.getNumber(),
                inv.getPartyId(), customerName,
                inv.getCurrency(),
                inv.getSubtotal(), inv.getTaxAmount(), inv.getTotal(),
                lineDtos);
    }

    @Transactional(readOnly = true)
    public byte[] generateCreditNotePdf(UUID id) {
        CreditNote cn = creditNotes.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.credit_note", id));
        PartnerSummary customer = customerLookup.findById(cn.getPartyId()).orElse(null);
        List<CreditNoteLine> lines = creditNoteLines.findByCreditNoteIdOrderByLineNumberAsc(id);
        Invoice inv = invoices.findById(cn.getInvoiceId()).orElse(null);
        Map<String, Object> vars = buildCreditNoteVars(cn, lines, customer, inv);
        return renderer.renderPdf(PdfRenderRequest.of("credit-note", vars));
    }

    // ── PDF generation ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(UUID id) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> NotFoundException.of("entity.invoice", id));
        PartnerSummary customer = customerLookup.findById(inv.getPartyId()).orElse(null);
        List<InvoiceLine> lines = invoiceLines.findByInvoiceIdOrderByLineNumberAsc(id);
        Map<String, Object> vars = buildInvoiceVars(inv, lines, customer);
        return renderer.renderPdf(PdfRenderRequest.of("invoice", vars));
    }

    @Transactional(readOnly = true)
    public byte[] generateQuotePdf(UUID id) {
        Quote q = quotes.findById(id).orElseThrow(() -> NotFoundException.of("entity.quote", id));
        PartnerSummary customer = customerLookup.findById(q.getPartyId()).orElse(null);
        List<QuoteLine> lines = quoteLines.findByQuoteIdOrderByLineNumberAsc(id);
        Map<String, Object> vars = buildQuoteVars(q, lines, customer);
        return renderer.renderPdf(PdfRenderRequest.of("quote", vars));
    }

    @Transactional(readOnly = true)
    public byte[] generateOrderPdf(UUID id) {
        Order o = orders.findById(id).orElseThrow(() -> NotFoundException.of("entity.order", id));
        PartnerSummary customer = customerLookup.findById(o.getPartyId()).orElse(null);
        List<OrderLine> lines = orderLines.findByOrderIdOrderByLineNumberAsc(id);
        Map<String, Object> vars = buildOrderVars(o, lines, customer);
        return renderer.renderPdf(PdfRenderRequest.of("order", vars));
    }

    // ── Overdue job ──────────────────────────────────────────────────────────

    @Scheduled(cron = "0 0 7 * * *")
    @Transactional
    public void detectOverdueInvoices() {
        log.info("Running overdue invoice detection job");
        invoices.findOverdue(LocalDate.now()).forEach(inv -> {
            log.info("Marking invoice {} as OVERDUE", inv.getNumber());
            markOverdue(inv.getId());
        });
    }

    // ── Line builders ─────────────────────────────────────────────────────────

    private List<QuoteLine> buildQuoteLines(UUID quoteId, List<SalesDto.LineRequest> lineReqs, String currency) {
        List<QuoteLine> result = new ArrayList<>();
        int i = 1;
        for (SalesDto.LineRequest lr : lineReqs) {
            var product = catalog.findProductById(lr.productId()).orElseThrow(
                    () -> NotFoundException.of("entity.product", lr.productId()));
            BigDecimal disc = lr.discountPercent() != null ? lr.discountPercent() : BigDecimal.ZERO;
            BigDecimal discFactor = BigDecimal.ONE.subtract(disc.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            BigDecimal lineTotal = lr.unitPrice().multiply(lr.quantity()).multiply(discFactor).setScale(2, RoundingMode.HALF_UP);
            QuoteLine line = QuoteLine.builder()
                    .quoteId(quoteId).lineNumber(i++).productId(lr.productId())
                    .uomId(lr.uomId() != null ? lr.uomId() : product.baseUomId())
                    .quantity(lr.quantity()).unitPrice(lr.unitPrice())
                    .discountPercent(disc).taxRate(BigDecimal.ZERO).lineTotal(lineTotal)
                    .snapshotName(product.name()).snapshotSku(product.sku())
                    .build();
            quoteLines.save(line);
            result.add(line);
        }
        return result;
    }

    private List<OrderLine> buildOrderLines(UUID orderId, List<SalesDto.LineRequest> lineReqs) {
        List<OrderLine> result = new ArrayList<>();
        int i = 1;
        for (SalesDto.LineRequest lr : lineReqs) {
            var product = catalog.findProductById(lr.productId()).orElseThrow(
                    () -> NotFoundException.of("entity.product", lr.productId()));
            BigDecimal disc = lr.discountPercent() != null ? lr.discountPercent() : BigDecimal.ZERO;
            BigDecimal discFactor = BigDecimal.ONE.subtract(disc.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            BigDecimal lineTotal = lr.unitPrice().multiply(lr.quantity()).multiply(discFactor).setScale(2, RoundingMode.HALF_UP);
            OrderLine line = OrderLine.builder()
                    .orderId(orderId).lineNumber(i++).productId(lr.productId())
                    .uomId(lr.uomId() != null ? lr.uomId() : product.baseUomId())
                    .quantity(lr.quantity()).unitPrice(lr.unitPrice())
                    .discountPercent(disc).taxRate(BigDecimal.ZERO).lineTotal(lineTotal)
                    .snapshotName(product.name()).snapshotSku(product.sku())
                    .build();
            orderLines.save(line);
            result.add(line);
        }
        return result;
    }

    private List<InvoiceLine> buildInvoiceLines(UUID invoiceId, List<SalesDto.LineRequest> lineReqs) {
        List<InvoiceLine> result = new ArrayList<>();
        int i = 1;
        for (SalesDto.LineRequest lr : lineReqs) {
            var product = catalog.findProductById(lr.productId()).orElseThrow(
                    () -> NotFoundException.of("entity.product", lr.productId()));
            BigDecimal disc = lr.discountPercent() != null ? lr.discountPercent() : BigDecimal.ZERO;
            BigDecimal discFactor = BigDecimal.ONE.subtract(disc.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
            BigDecimal lineTotal = lr.unitPrice().multiply(lr.quantity()).multiply(discFactor).setScale(2, RoundingMode.HALF_UP);
            InvoiceLine line = InvoiceLine.builder()
                    .invoiceId(invoiceId).lineNumber(i++).productId(lr.productId())
                    .uomId(lr.uomId() != null ? lr.uomId() : product.baseUomId())
                    .quantity(lr.quantity()).unitPrice(lr.unitPrice())
                    .discountPercent(disc).taxRate(BigDecimal.ZERO).lineTotal(lineTotal)
                    .snapshotName(product.name()).snapshotSku(product.sku())
                    .build();
            invoiceLines.save(line);
            result.add(line);
        }
        return result;
    }

    private void computeQuoteTotals(Quote q, List<QuoteLine> lines) {
        BigDecimal sub = lines.stream().map(QuoteLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        q.setSubtotal(sub);
        q.setTotal(sub.subtract(q.getDiscountAmount()).add(q.getTaxAmount()));
    }

    private void computeOrderTotals(Order o, List<OrderLine> lines) {
        BigDecimal sub = lines.stream().map(OrderLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        o.setSubtotal(sub);
        o.setTotal(sub.subtract(o.getDiscountAmount()).add(o.getTaxAmount()));
    }

    private void computeInvoiceTotals(Invoice inv, List<InvoiceLine> lines) {
        BigDecimal sub = lines.stream().map(InvoiceLine::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        inv.setSubtotal(sub);
        inv.setTotal(sub.subtract(inv.getDiscountAmount()).add(inv.getTaxAmount()));
        inv.setBalance(inv.getTotal());
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private SalesDto.LineDto toLineDto(QuoteLine l) {
        return new SalesDto.LineDto(l.getId(), l.getLineNumber(), l.getProductId(), l.getUomId(),
                l.getQuantity(), l.getUnitPrice(), l.getDiscountPercent(), l.getTaxRate(),
                l.getLineTotal(), l.getSnapshotName(), l.getSnapshotSku());
    }

    private SalesDto.LineDto toLineDto(OrderLine l) {
        return new SalesDto.LineDto(l.getId(), l.getLineNumber(), l.getProductId(), l.getUomId(),
                l.getQuantity(), l.getUnitPrice(), l.getDiscountPercent(), l.getTaxRate(),
                l.getLineTotal(), l.getSnapshotName(), l.getSnapshotSku());
    }

    private SalesDto.LineDto toLineDto(InvoiceLine l) {
        return new SalesDto.LineDto(l.getId(), l.getLineNumber(), l.getProductId(), l.getUomId(),
                l.getQuantity(), l.getUnitPrice(), l.getDiscountPercent(), l.getTaxRate(),
                l.getLineTotal(), l.getSnapshotName(), l.getSnapshotSku());
    }

    private SalesDto.QuoteDto toQuoteDto(Quote q, List<QuoteLine> lines, String customerName) {
        return new SalesDto.QuoteDto(q.getId(), q.getNumber(), q.getPartyId(), customerName,
                q.getIssueDate(), q.getValidUntil(), q.getStatus().name(), q.getCurrency(),
                q.getSubtotal(), q.getDiscountAmount(), q.getTaxAmount(), q.getTotal(),
                q.getNotes(), lines.stream().map(this::toLineDto).toList(), q.getCreatedAt());
    }

    private SalesDto.OrderDto toOrderDto(Order o, List<OrderLine> lines, String customerName) {
        return new SalesDto.OrderDto(o.getId(), o.getNumber(), o.getPartyId(), customerName,
                o.getQuoteId(), o.getOrderDate(), o.getStatus().name(), o.isDeliveryRequired(),
                o.getCurrency(), o.getSubtotal(), o.getDiscountAmount(), o.getTaxAmount(), o.getTotal(),
                o.getNotes(), lines.stream().map(this::toLineDto).toList(), o.getCreatedAt());
    }

    private SalesDto.InvoiceDto toInvoiceDto(Invoice inv, List<InvoiceLine> lines, String customerName) {
        return new SalesDto.InvoiceDto(inv.getId(), inv.getNumber(), inv.getPartyId(), customerName,
                inv.getOrderId(), inv.getIssueDate(), inv.getDueDate(), inv.getStatus().name(),
                inv.getCurrency(), inv.getSubtotal(), inv.getDiscountAmount(), inv.getTaxAmount(),
                inv.getTotal(), inv.getPaidAmount(), inv.getBalance(),
                inv.getPaymentTerms(), inv.getNotes(),
                lines.stream().map(this::toLineDto).toList(), inv.getCreatedAt());
    }

    private SalesDto.CreditNoteDto toCreditNoteDto(CreditNote cn) {
        String invoiceNumber = invoices.findById(cn.getInvoiceId())
                .map(Invoice::getNumber).orElse("");
        String customerName = customerLookup.findById(cn.getPartyId())
                .map(PartnerSummary::name).orElse("");
        List<SalesDto.CreditNoteLineDto> lineDtos = creditNoteLines
                .findByCreditNoteIdOrderByLineNumberAsc(cn.getId()).stream()
                .map(l -> new SalesDto.CreditNoteLineDto(
                        l.getId(), l.getInvoiceLineId(), l.getProductId(), l.getUomId(),
                        l.getQuantity(), l.getUnitPrice(), l.getDiscountPercent(), l.getTaxRate(),
                        l.getLineTotal(), l.getReturnedToStockQty(),
                        l.getSnapshotName(), l.getSnapshotSku()))
                .toList();
        return new SalesDto.CreditNoteDto(cn.getId(), cn.getNumber(),
                cn.getInvoiceId(), invoiceNumber,
                cn.getPartyId(), customerName,
                cn.getIssueDate(), cn.getReason(),
                cn.getSubtotal(), cn.getTaxAmount(), cn.getTotal(),
                cn.getStatus().name(), cn.getCurrency(),
                lineDtos, cn.getCreatedAt());
    }

    private InvoiceSummary toInvoiceSummary(Invoice inv) {
        return new InvoiceSummary(inv.getId(), inv.getNumber(), inv.getPartyId(),
                inv.getDueDate(), inv.getTotal(), inv.getPaidAmount(),
                inv.getBalance(), inv.getStatus().name());
    }

    private Map<String, Object> buildInvoiceVars(Invoice inv, List<InvoiceLine> lines, PartnerSummary customer) {
        String payStatus = switch (inv.getStatus()) {
            case PAID -> "PAYÉE";
            case PARTIAL -> "PARTIELLE";
            default -> "NON PAYÉE";
        };
        record LineModel(String productName, String sku, BigDecimal quantity, BigDecimal unitPrice,
                         BigDecimal discountPercent, BigDecimal taxRate, BigDecimal lineTotal) {}
        record InvoiceModel(String number, LocalDate issueDate, LocalDate dueDate,
                            String paymentStatus, String paymentStatusLabel,
                            BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount,
                            BigDecimal total, BigDecimal paidAmount, BigDecimal balance,
                            String currency, String paymentTerms, String notes,
                            List<LineModel> lines) {}
        record CustomerModel(String name, String address, String phone, String email) {}
        var linesModel = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantity(), l.getUnitPrice(), l.getDiscountPercent(), l.getTaxRate(), l.getLineTotal())).toList();
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("invoice", new InvoiceModel(inv.getNumber(), inv.getIssueDate(), inv.getDueDate(),
                inv.getStatus().name().toLowerCase(), payStatus,
                inv.getSubtotal(), inv.getDiscountAmount(), inv.getTaxAmount(),
                inv.getTotal(), inv.getPaidAmount(), inv.getBalance(),
                inv.getCurrency(), inv.getPaymentTerms(), inv.getNotes(), linesModel));
        vars.put("customer", new CustomerModel(
                customer != null ? customer.name() : "",
                customer != null ? customer.address() : "",
                customer != null ? customer.phone() : "",
                customer != null ? customer.email() : ""));
        return vars;
    }

    private Map<String, Object> buildCreditNoteVars(CreditNote cn, List<CreditNoteLine> lines,
                                                    PartnerSummary customer, Invoice originalInvoice) {
        record LineModel(String productName, String sku, BigDecimal quantity, BigDecimal unitPrice,
                         BigDecimal discountPercent, BigDecimal taxRate, BigDecimal lineTotal) {}
        record CreditNoteModel(String number, LocalDate issueDate, String reason,
                               BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total,
                               String currency, String invoiceNumber, List<LineModel> lines) {}
        record CustomerModel(String name, String address, String phone, String email) {}
        var lm = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantity(), l.getUnitPrice(), l.getDiscountPercent(), l.getTaxRate(),
                l.getLineTotal())).toList();
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("creditNote", new CreditNoteModel(cn.getNumber(), cn.getIssueDate(), cn.getReason(),
                cn.getSubtotal(), cn.getTaxAmount(), cn.getTotal(),
                cn.getCurrency(),
                originalInvoice != null ? originalInvoice.getNumber() : "",
                lm));
        vars.put("customer", new CustomerModel(
                customer != null ? customer.name() : "",
                customer != null ? customer.address() : "",
                customer != null ? customer.phone() : "",
                customer != null ? customer.email() : ""));
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

    private Map<String, Object> buildOrderVars(Order o, List<OrderLine> lines, PartnerSummary customer) {
        record LineModel(String productName, String sku, BigDecimal quantity, BigDecimal unitPrice,
                         BigDecimal discountPercent, BigDecimal taxRate, BigDecimal lineTotal) {}
        record OrderModel(String number, LocalDate orderDate, String statusLabel,
                          BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount,
                          BigDecimal total, String currency, String notes, List<LineModel> lines) {}
        record CustomerModel(String name, String address, String phone) {}
        var lm = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantity(), l.getUnitPrice(), l.getDiscountPercent(), l.getTaxRate(), l.getLineTotal())).toList();
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("order", new OrderModel(o.getNumber(), o.getOrderDate(), o.getStatus().name(),
                o.getSubtotal(), o.getDiscountAmount(), o.getTaxAmount(), o.getTotal(),
                o.getCurrency(), o.getNotes(), lm));
        vars.put("customer", new CustomerModel(
                customer != null ? customer.name() : "",
                customer != null ? customer.address() : "",
                customer != null ? customer.phone() : ""));
        return vars;
    }

    private Map<String, Object> buildQuoteVars(Quote q, List<QuoteLine> lines, PartnerSummary customer) {
        record LineModel(String productName, String sku, BigDecimal quantity, BigDecimal unitPrice,
                         BigDecimal taxRate, BigDecimal lineTotal) {}
        record QuoteModel(String number, LocalDate issueDate, LocalDate validUntil,
                          BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total,
                          String currency, String notes, List<LineModel> lines) {}
        record CustomerModel(String name, String address, String phone) {}
        var lm = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantity(), l.getUnitPrice(), l.getTaxRate(), l.getLineTotal())).toList();
        Map<String, Object> vars = new HashMap<>(brandingVars());
        vars.put("quote", new QuoteModel(q.getNumber(), q.getIssueDate(), q.getValidUntil(),
                q.getSubtotal(), q.getTaxAmount(), q.getTotal(), q.getCurrency(), q.getNotes(), lm));
        vars.put("customer", new CustomerModel(
                customer != null ? customer.name() : "",
                customer != null ? customer.address() : "",
                customer != null ? customer.phone() : ""));
        return vars;
    }
}
