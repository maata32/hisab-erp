package com.minierp.sales.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.partner.api.ArBalanceOperations;
import com.minierp.partner.api.PartnerLookup;
import com.minierp.partner.api.PartnerSummary;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.sales.api.InvoiceOperations;
import com.minierp.sales.api.InvoiceSummary;
import com.minierp.sales.api.SalesDto;
import com.minierp.sales.api.SalesStatementLookup;
import com.minierp.sales.api.StatementCreditNoteEntry;
import com.minierp.sales.api.StatementInvoiceEntry;
import com.minierp.sales.api.StatementInvoiceLine;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.util.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesService implements InvoiceOperations, SalesStatementLookup {

    private final QuoteRepository quotes;
    private final QuoteLineRepository quoteLines;
    private final OrderRepository orders;
    private final OrderLineRepository orderLines;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository invoiceLines;
    private final CreditNoteRepository creditNotes;
    private final NumberingService numbering;
    private final CatalogLookup catalog;
    private final PartnerLookup customerLookup;
    private final ArBalanceOperations balanceOps;
    private final DocumentRenderer renderer;

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
        o.setStatus(OrderStatus.valueOf(status));
        String name = customerLookup.findById(o.getPartyId()).map(PartnerSummary::name).orElse("");
        return toOrderDto(o, orderLines.findByOrderIdOrderByLineNumberAsc(id), name);
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

    /**
     * Cancel an unpaid invoice. Reverses the invoiced amount on the customer balance
     * so the customer is no longer expected to pay it. Refuses to cancel anything
     * already paid (PARTIAL/PAID) — those must go through a credit note instead.
     */
    @Transactional
    public SalesDto.InvoiceDto cancelInvoice(UUID id) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> NotFoundException.of("entity.invoice", id));
        if (inv.getStatus() == InvoiceStatus.CANCELLED) {
            String name = customerLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
            return toInvoiceDto(inv, invoiceLines.findByInvoiceIdOrderByLineNumberAsc(id), name);
        }
        if (inv.getStatus() == InvoiceStatus.PAID || inv.getStatus() == InvoiceStatus.PARTIAL) {
            throw new BusinessException("error.sales.invoice_already_paid",
                    Map.of("status", inv.getStatus().name()));
        }
        balanceOps.addToInvoiced(inv.getPartyId(), inv.getTotal().negate());
        inv.setStatus(InvoiceStatus.CANCELLED);
        String name = customerLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
        return toInvoiceDto(inv, invoiceLines.findByInvoiceIdOrderByLineNumberAsc(id), name);
    }

    // ── Credit notes ─────────────────────────────────────────────────────────

    @Transactional
    public SalesDto.CreditNoteDto createCreditNote(SalesDto.CreateCreditNoteRequest req) {
        Invoice inv = invoices.findById(req.invoiceId())
                .orElseThrow(() -> NotFoundException.of("entity.invoice", req.invoiceId()));
        String number = numbering.next(DocumentType.CREDIT_NOTE);
        CreditNote cn = CreditNote.builder()
                .number(number)
                .invoiceId(req.invoiceId())
                .partyId(inv.getPartyId())
                .issueDate(LocalDate.now())
                .reason(req.reason())
                .amount(req.amount())
                .currency(inv.getCurrency())
                .status(CreditNoteStatus.ISSUED)
                .build();
        creditNotes.save(cn);
        return toCreditNoteDto(cn);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesDto.CreditNoteDto> listCreditNotes(UUID customerId, Pageable pageable) {
        var page = customerId != null
                ? creditNotes.findByPartyId(customerId, pageable)
                : creditNotes.findAll(pageable);
        return PageResponse.of(page.map(this::toCreditNoteDto));
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
        return new SalesDto.CreditNoteDto(cn.getId(), cn.getNumber(), cn.getInvoiceId(),
                cn.getPartyId(), cn.getIssueDate(), cn.getReason(), cn.getAmount(),
                cn.getStatus().name(), cn.getCurrency(), cn.getCreatedAt());
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
        return Map.of(
                "invoice", new InvoiceModel(inv.getNumber(), inv.getIssueDate(), inv.getDueDate(),
                        inv.getStatus().name().toLowerCase(), payStatus,
                        inv.getSubtotal(), inv.getDiscountAmount(), inv.getTaxAmount(),
                        inv.getTotal(), inv.getPaidAmount(), inv.getBalance(),
                        inv.getCurrency(), inv.getPaymentTerms(), inv.getNotes(), linesModel),
                "customer", new CustomerModel(
                        customer != null ? customer.name() : "",
                        customer != null ? "" : "",
                        customer != null ? customer.phone() : "",
                        customer != null ? customer.email() : ""),
                "orgName", "Mini-ERP",
                "orgAddress", "",
                "orgPhone", "",
                "orgEmail", "",
                "logoUrl", ""
        );
    }

    private Map<String, Object> buildOrderVars(Order o, List<OrderLine> lines, PartnerSummary customer) {
        record LineModel(String productName, String sku, BigDecimal quantity, BigDecimal unitPrice,
                         BigDecimal discountPercent, BigDecimal taxRate, BigDecimal lineTotal) {}
        record OrderModel(String number, LocalDate orderDate, String statusLabel,
                          BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount,
                          BigDecimal total, String currency, String notes, List<LineModel> lines) {}
        record CustomerModel(String name, String phone) {}
        var lm = lines.stream().map(l -> new LineModel(l.getSnapshotName(), l.getSnapshotSku(),
                l.getQuantity(), l.getUnitPrice(), l.getDiscountPercent(), l.getTaxRate(), l.getLineTotal())).toList();
        return Map.of(
                "order", new OrderModel(o.getNumber(), o.getOrderDate(), o.getStatus().name(),
                        o.getSubtotal(), o.getDiscountAmount(), o.getTaxAmount(), o.getTotal(),
                        o.getCurrency(), o.getNotes(), lm),
                "customer", new CustomerModel(customer != null ? customer.name() : "",
                        customer != null ? customer.phone() : ""),
                "orgName", "Mini-ERP",
                "orgAddress", "",
                "orgPhone", "",
                "logoUrl", ""
        );
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
        return Map.of(
                "quote", new QuoteModel(q.getNumber(), q.getIssueDate(), q.getValidUntil(),
                        q.getSubtotal(), q.getTaxAmount(), q.getTotal(), q.getCurrency(), q.getNotes(), lm),
                "customer", new CustomerModel(customer != null ? customer.name() : "", "", customer != null ? customer.phone() : ""),
                "orgName", "Mini-ERP", "orgAddress", "", "logoUrl", ""
        );
    }
}
