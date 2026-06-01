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
import com.minierp.shared.tenant.TenantContext;
import com.minierp.shared.util.PageResponse;
import com.minierp.tenant.api.TenantLookup;
import com.minierp.tenant.api.TenantSettingsLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.minierp.partner.api.CustomerCreditOperations;
import com.minierp.sales.api.CreditNoteAppliedEvent;
import com.minierp.sales.api.CreditNoteReturnRequestedEvent;
import com.minierp.sales.api.InvoicePaymentsDetachedEvent;
import org.springframework.context.ApplicationEventPublisher;
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
public class SalesService implements InvoiceOperations, SalesStatementLookup, com.minierp.sales.api.InvoiceWriteOperations {

    private final QuoteRepository quotes;
    private final QuoteLineRepository quoteLines;
    private final InvoiceRepository invoices;
    private final InvoiceLineRepository invoiceLines;
    private final CreditNoteRepository creditNotes;
    private final CreditNoteLineRepository creditNoteLines;
    private final NumberingService numbering;
    private final CatalogLookup catalog;
    private final PartnerLookup customerLookup;
    private final ArBalanceOperations balanceOps;
    private final CustomerCreditOperations customerCreditOps;
    private final JdbcTemplate jdbc;
    private final DocumentRenderer renderer;
    private final TenantLookup tenantLookup;
    private final TenantSettingsLookup tenantSettings;
    private final ApplicationEventPublisher events;

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
                        cn.getId(), cn.getInvoiceId(), cn.getNumber(), cn.getIssueDate(),
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

    // ── InvoiceOperations ───────────────────────────────────────────────────

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
    public void reversePayment(UUID invoiceId, BigDecimal amount) {
        Invoice inv = invoices.lockById(invoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.invoice", invoiceId));
        BigDecimal newPaid = inv.getPaidAmount().subtract(amount).max(BigDecimal.ZERO);
        inv.setPaidAmount(newPaid);
        inv.setBalance(inv.getTotal().subtract(newPaid).max(BigDecimal.ZERO));
        if (newPaid.signum() == 0) {
            // Unpay the invoice entirely: walk status back. CANCELLED stays
            // CANCELLED (a refunded cancelled invoice is still cancelled).
            if (inv.getStatus() == InvoiceStatus.PAID || inv.getStatus() == InvoiceStatus.PARTIAL
                    || inv.getStatus() == InvoiceStatus.OVERDUE) {
                inv.setStatus(InvoiceStatus.ISSUED);
            }
        } else if (inv.getStatus() == InvoiceStatus.PAID) {
            inv.setStatus(InvoiceStatus.PARTIAL);
        }
        balanceOps.addToPaid(inv.getPartyId(), amount.negate(), false);
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

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getInvoicedQtyByProduct(UUID invoiceId) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (InvoiceLine il : invoiceLines.findByInvoiceIdOrderByLineNumberAsc(invoiceId)) {
            map.merge(il.getProductId(), il.getQuantity(), BigDecimal::add);
        }
        return map;
    }

    @Override
    @Transactional
    public void recomputeDeliveryStatus(UUID invoiceId, Map<UUID, BigDecimal> totalDeliveredByProduct) {
        Invoice inv = invoices.findById(invoiceId).orElse(null);
        if (inv == null) return;
        if (inv.getStatus() == InvoiceStatus.DRAFT || inv.getStatus() == InvoiceStatus.CANCELLED) {
            return;
        }
        // A fully-credited invoice keeps the RETURNED delivery status set when its
        // avoir was issued — don't recompute it back to DELIVERED.
        if (inv.getDeliveryStatus() == InvoiceDeliveryStatus.RETURNED
                && creditNotes.countNonDraftByInvoiceId(invoiceId) > 0) {
            return;
        }
        Map<UUID, BigDecimal> invoicedByProduct = getInvoicedQtyByProduct(invoiceId);
        Map<UUID, BigDecimal> creditedByProduct = aggregateCreditedByProduct(invoiceId);
        boolean allCovered = !invoicedByProduct.isEmpty();
        boolean anyDelivered = false;
        for (Map.Entry<UUID, BigDecimal> e : invoicedByProduct.entrySet()) {
            // Credits cancel the obligation to deliver — a fully credited line no
            // longer leaves anything outstanding even if it was never shipped.
            BigDecimal credited = creditedByProduct.getOrDefault(e.getKey(), BigDecimal.ZERO);
            BigDecimal effectiveInvoiced = e.getValue().subtract(credited).max(BigDecimal.ZERO);
            BigDecimal delivered = totalDeliveredByProduct.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (delivered.signum() > 0) anyDelivered = true;
            if (delivered.compareTo(effectiveInvoiced) < 0) allCovered = false;
        }
        InvoiceDeliveryStatus next;
        if (allCovered) {
            next = InvoiceDeliveryStatus.DELIVERED;
        } else if (anyDelivered) {
            next = InvoiceDeliveryStatus.PARTIALLY_DELIVERED;
        } else {
            next = InvoiceDeliveryStatus.NONE;
        }
        if (next != inv.getDeliveryStatus()) {
            inv.setDeliveryStatus(next);
        }
    }

    private Map<UUID, BigDecimal> aggregateCreditedByProduct(UUID invoiceId) {
        Map<UUID, BigDecimal> map = new HashMap<>();
        for (Object[] row : creditNoteLines.sumCreditedByProduct(invoiceId)) {
            map.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canReceiveDelivery(UUID invoiceId) {
        return invoices.findById(invoiceId)
                .map(inv -> inv.getStatus() != InvoiceStatus.DRAFT
                        && inv.getStatus() != InvoiceStatus.CANCELLED
                        // A fully-credited invoice (settled by an avoir) accepts no delivery.
                        && creditNotes.countNonDraftByInvoiceId(invoiceId) == 0
                        && inv.getDeliveryStatus() != InvoiceDeliveryStatus.DELIVERED
                        && inv.getDeliveryStatus() != InvoiceDeliveryStatus.RETURNED)
                .orElse(false);
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
    public SalesDto.QuoteDto updateQuote(UUID id, SalesDto.UpdateQuoteRequest req) {
        Quote q = quotes.findById(id).orElseThrow(() -> NotFoundException.of("entity.quote", id));
        if (q.getStatus() == QuoteStatus.CONVERTED
                || q.getStatus() == QuoteStatus.REJECTED) {
            throw new BusinessException("error.quote.not_editable",
                    Map.of("status", q.getStatus().name()));
        }
        if (req.lines() == null || req.lines().isEmpty()) {
            throw new BusinessException("error.quote.no_lines", Map.of());
        }
        if (req.issueDate() != null) q.setIssueDate(req.issueDate());
        q.setValidUntil(req.validUntil());
        if (req.currency() != null) q.setCurrency(req.currency());
        q.setNotes(req.notes());

        quoteLines.deleteByQuoteId(id);
        quoteLines.flush();
        List<QuoteLine> built = buildQuoteLines(q.getId(), req.lines(), q.getCurrency());
        computeQuoteTotals(q, built);

        String name = customerLookup.findById(q.getPartyId()).map(PartnerSummary::name).orElse("");
        return toQuoteDto(q, built, name);
    }

    @Transactional
    public SalesDto.InvoiceDto convertQuoteToInvoice(UUID quoteId, LocalDate dueDate, String paymentTerms) {
        Quote q = quotes.findById(quoteId).orElseThrow(() -> NotFoundException.of("entity.quote", quoteId));
        if (q.getStatus() != QuoteStatus.DRAFT && q.getStatus() != QuoteStatus.SENT && q.getStatus() != QuoteStatus.ACCEPTED) {
            throw new BusinessException("error.sales.quote_not_convertible", Map.of("status", q.getStatus()));
        }
        String invNumber = numbering.next(DocumentType.INVOICE);
        Invoice inv = Invoice.builder()
                .number(invNumber)
                .partyId(q.getPartyId())
                .quoteId(quoteId)
                .issueDate(LocalDate.now())
                .dueDate(dueDate)
                .status(InvoiceStatus.DRAFT)
                .currency(q.getCurrency())
                .subtotal(q.getSubtotal())
                .discountAmount(q.getDiscountAmount())
                .taxAmount(q.getTaxAmount())
                .total(q.getTotal())
                .balance(q.getTotal())
                .paymentTerms(paymentTerms)
                .notes(q.getNotes())
                .build();
        invoices.save(inv);

        List<QuoteLine> qLines = quoteLines.findByQuoteIdOrderByLineNumberAsc(quoteId);
        for (QuoteLine ql : qLines) {
            invoiceLines.save(InvoiceLine.builder()
                    .invoiceId(inv.getId())
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
        q.setConvertedToInvoiceId(inv.getId());

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
                .quoteId(req.quoteId())
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

    /**
     * Clone an invoice into a brand-new ISSUED one. Lines (snapshots and all)
     * are copied verbatim. The new invoice has no quote link and is issued today
     * with a fresh due date offset by the same number of days as the source.
     */
    @Transactional
    public SalesDto.InvoiceDto duplicateInvoice(UUID id) {
        Invoice src = invoices.findById(id)
                .orElseThrow(() -> NotFoundException.of("entity.invoice", id));
        List<InvoiceLine> srcLines = invoiceLines.findByInvoiceIdOrderByLineNumberAsc(id);
        if (srcLines.isEmpty()) {
            throw new BusinessException("error.invoice.no_lines", Map.of("invoiceId", id));
        }

        LocalDate today = LocalDate.now();
        LocalDate dueDate = src.getDueDate() != null && src.getIssueDate() != null
                ? today.plusDays(java.time.temporal.ChronoUnit.DAYS.between(src.getIssueDate(), src.getDueDate()))
                : today.plusDays(30);

        String number = numbering.next(DocumentType.INVOICE);
        Invoice copy = Invoice.builder()
                .number(number)
                .partyId(src.getPartyId())
                .quoteId(null)
                .issueDate(today)
                .dueDate(dueDate)
                .status(InvoiceStatus.ISSUED)
                .currency(src.getCurrency())
                .paymentTerms(src.getPaymentTerms())
                .notes(src.getNotes())
                .build();
        invoices.save(copy);

        List<InvoiceLine> built = new ArrayList<>();
        for (InvoiceLine sl : srcLines) {
            InvoiceLine line = InvoiceLine.builder()
                    .invoiceId(copy.getId())
                    .lineNumber(sl.getLineNumber())
                    .productId(sl.getProductId())
                    .uomId(sl.getUomId())
                    .quantity(sl.getQuantity())
                    .unitPrice(sl.getUnitPrice())
                    .discountPercent(sl.getDiscountPercent())
                    .taxRate(sl.getTaxRate())
                    .lineTotal(sl.getLineTotal())
                    .snapshotName(sl.getSnapshotName())
                    .snapshotSku(sl.getSnapshotSku())
                    .build();
            invoiceLines.save(line);
            built.add(line);
        }
        computeInvoiceTotals(copy, built);
        balanceOps.addToInvoiced(copy.getPartyId(), copy.getTotal());

        String name = customerLookup.findById(copy.getPartyId()).map(PartnerSummary::name).orElse("");
        return toInvoiceDto(copy, built, name);
    }

    @Transactional
    public SalesDto.InvoiceDto issueInvoice(UUID id) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> NotFoundException.of("entity.invoice", id));
        if (inv.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessException("error.invoice.not_draft", Map.of("status", inv.getStatus()));
        }
        inv.setStatus(InvoiceStatus.ISSUED);
        balanceOps.addToInvoiced(inv.getPartyId(), inv.getTotal());
        String name = customerLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
        return toInvoiceDto(inv, invoiceLines.findByInvoiceIdOrderByLineNumberAsc(id), name);
    }

    @Transactional
    public SalesDto.InvoiceDto cancelInvoice(UUID id) {
        Invoice inv = invoices.findById(id).orElseThrow(() -> NotFoundException.of("entity.invoice", id));
        if (inv.getStatus() != InvoiceStatus.DRAFT) {
            throw new BusinessException("error.invoice.not_draft", Map.of("status", inv.getStatus()));
        }
        inv.setStatus(InvoiceStatus.CANCELLED);
        String name = customerLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
        return toInvoiceDto(inv, invoiceLines.findByInvoiceIdOrderByLineNumberAsc(id), name);
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
        List<UUID> ids = page.getContent().stream().map(Invoice::getId).toList();
        Map<UUID, Long> cnCountByInvoice = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : creditNotes.countNonDraftByInvoiceIds(ids)) {
                cnCountByInvoice.put((UUID) row[0], (Long) row[1]);
            }
        }
        return PageResponse.of(page.map(inv -> {
            String name = customerLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");
            long cnCount = cnCountByInvoice.getOrDefault(inv.getId(), 0L);
            return toInvoiceDto(inv, invoiceLines.findByInvoiceIdOrderByLineNumberAsc(inv.getId()), name, cnCount);
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
        ensureCreditable(inv);

        List<InvoiceLine> invLines = invoiceLines.findByInvoiceIdOrderByLineNumberAsc(invoiceId);
        if (invLines.isEmpty()) {
            throw new BusinessException("error.creditnote.invoice_has_no_lines",
                    Map.of("invoiceId", inv.getId()));
        }
        Map<UUID, BigDecimal> deliveredByProduct = aggregateDeliveredByProductForInvoice(invoiceId);

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

        // Total avoir: one CreditNoteLine per InvoiceLine, full quantity. Stock
        // return is exactly what has been delivered per product — never-shipped
        // quantities cancel without a BR.
        List<CreditNoteReturnRequestedEvent.ReturnLine> returnLines = new ArrayList<>();
        Map<UUID, BigDecimal> productReturnedAcc = new HashMap<>();
        BigDecimal subtotalHt = BigDecimal.ZERO;
        int lineNo = 1;
        for (InvoiceLine il : invLines) {
            BigDecimal lineTotal = il.getLineTotal();
            BigDecimal delivered = deliveredByProduct.getOrDefault(il.getProductId(), BigDecimal.ZERO);
            BigDecimal alreadyAttributed = productReturnedAcc.getOrDefault(il.getProductId(), BigDecimal.ZERO);
            // A product may appear on several lines; the delivered-pool is
            // global per product, so spread it across lines in order to avoid
            // double-returning the same units.
            BigDecimal stockReturnQty = delivered.subtract(alreadyAttributed)
                    .max(BigDecimal.ZERO)
                    .min(il.getQuantity());
            if (stockReturnQty.signum() > 0) {
                productReturnedAcc.merge(il.getProductId(), stockReturnQty, BigDecimal::add);
            }

            CreditNoteLine cnl = CreditNoteLine.builder()
                    .creditNoteId(cn.getId())
                    .lineNumber(lineNo++)
                    .invoiceLineId(il.getId())
                    .productId(il.getProductId())
                    .uomId(il.getUomId())
                    .quantity(il.getQuantity())
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

        // Aggregate per-product stock-return rows for the BR event.
        for (Map.Entry<UUID, BigDecimal> e : productReturnedAcc.entrySet()) {
            if (e.getValue().signum() <= 0) continue;
            InvoiceLine ref = invLines.stream()
                    .filter(l -> l.getProductId().equals(e.getKey()))
                    .findFirst().orElseThrow();
            returnLines.add(new CreditNoteReturnRequestedEvent.ReturnLine(
                    ref.getProductId(), ref.getUomId(), e.getValue(),
                    ref.getUnitPrice(), ref.getSnapshotName(), ref.getSnapshotSku()));
        }

        // Mirror the invoice totals 1:1 — avoir total = invoice total.
        BigDecimal taxAmount = inv.getTaxAmount();
        BigDecimal total = inv.getTotal();
        cn.setSubtotal(inv.getSubtotal());
        cn.setTaxAmount(taxAmount);
        cn.setTotal(total);
        cn.setAmount(total);

        // Detach the invoice from its payments: the avoir, not the cash, now
        // settles the invoice. The cash already received is freed back to the
        // customer as a refundable OVERPAYMENT credit — the detachment listener
        // mints one credit per payment (stamped with its source_payment_id, so
        // the engine never double-counts the freed payment) and soft-voids the
        // PAYMENT → INVOICE allocation rows.
        BigDecimal priorPaid = inv.getPaidAmount();
        if (priorPaid.signum() > 0) {
            BigDecimal newBalance = inv.getTotal();
            inv.setPaidAmount(BigDecimal.ZERO);
            inv.setBalance(newBalance);
            if (inv.getStatus() != InvoiceStatus.CANCELLED) {
                inv.setStatus(InvoiceStatus.ISSUED);
            }
            balanceOps.addToPaid(inv.getPartyId(), priorPaid.negate(), false);
            events.publishEvent(new InvoicePaymentsDetachedEvent(
                    inv.getId(), inv.getPartyId(), cn.getNumber()));
        }

        // Letter the avoir against the (now fully open) invoice balance.
        BigDecimal imputed = applyCredit(inv.getId(), total);
        cn.setAppliedToInvoiceId(inv.getId());
        cn.setStatus(CreditNoteStatus.APPLIED);

        // Surface the CREDIT_NOTE → INVOICE pairing in the unified allocations
        // audit table — keeps the engine's view consistent with the payment
        // double-write introduced in Phase 5.
        if (imputed.signum() > 0) {
            events.publishEvent(new CreditNoteAppliedEvent(
                    cn.getId(), cn.getNumber(), inv.getId(), inv.getPartyId(), imputed));
        }

        if (!returnLines.isEmpty()) {
            events.publishEvent(new CreditNoteReturnRequestedEvent(
                    cn.getId(), cn.getNumber(), inv.getId(), inv.getPartyId(),
                    List.copyOf(returnLines)));
        }

        // Mark the delivery side as RETURNED if anything was actually shipped;
        // the invoice itself stays PAID (settled by the avoir) — the linked
        // credit note is what makes it read as "soldée par avoir".
        inv.setDeliveryStatus(returnLines.isEmpty()
                ? InvoiceDeliveryStatus.NONE
                : InvoiceDeliveryStatus.RETURNED);

        return toCreditNoteDto(cn);
    }

    /**
     * Total-avoir gate: the invoice must exist in a creditable state and must
     * not already carry a non-DRAFT credit note (one avoir per invoice).
     */
    private void ensureCreditable(Invoice inv) {
        if (inv.getStatus() == InvoiceStatus.DRAFT) {
            throw new BusinessException("error.creditnote.invoice_draft",
                    Map.of("invoiceId", inv.getId(), "invoiceNumber", inv.getNumber()));
        }
        if (inv.getStatus() == InvoiceStatus.CANCELLED) {
            throw new BusinessException("error.creditnote.invoice_cancelled",
                    Map.of("invoiceId", inv.getId(), "invoiceNumber", inv.getNumber()));
        }
        long existing = creditNotes.countNonDraftByInvoiceId(inv.getId());
        if (existing > 0) {
            throw new BusinessException("error.creditnote.already_credited",
                    Map.of("invoiceId", inv.getId(), "invoiceNumber", inv.getNumber()));
        }
    }

    private Map<UUID, BigDecimal> aggregateDeliveredByProductForInvoice(UUID invoiceId) {
        // Cross-module read: delivered quantities live in the delivery module's tables.
        // A direct JDBC read keeps sales free of a compile-time dep on delivery.
        // Only OUTBOUND BLs count: a RETURN BL means goods coming back, not goods
        // shipped, and would inflate the "delivered" gross total.
        UUID tenant = TenantContext.require();
        Map<UUID, BigDecimal> map = new HashMap<>();
        jdbc.query("""
                SELECT dl.product_id, COALESCE(SUM(dl.quantity_delivered), 0)
                FROM delivery_lines dl
                JOIN deliveries d ON d.id = dl.delivery_id AND d.tenant_id = dl.tenant_id
                WHERE d.tenant_id = ? AND d.invoice_id = ? AND d.status <> 'CANCELLED'
                  AND d.type = 'OUTBOUND'
                GROUP BY dl.product_id
                """, rs -> {
            map.put(rs.getObject(1, UUID.class), rs.getBigDecimal(2));
        }, tenant, invoiceId);
        return map;
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesDto.CreditNoteDto> listCreditNotes(UUID customerId, UUID invoiceId, Pageable pageable) {
        var page = invoiceId != null
                ? creditNotes.findByInvoiceId(invoiceId, pageable)
                : customerId != null
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
    public SalesDto.CreditNotePreviewDto getCreditNotePreview(UUID invoiceId) {
        Invoice inv = invoices.findById(invoiceId)
                .orElseThrow(() -> NotFoundException.of("entity.invoice", invoiceId));
        String customerName = customerLookup.findById(inv.getPartyId()).map(PartnerSummary::name).orElse("");

        String blockReason = computeBlockReason(inv);
        BigDecimal creditAmount = inv.getTotal();
        BigDecimal alreadyPaid = inv.getPaidAmount();
        BigDecimal balance = inv.getBalance();
        BigDecimal toCustomerCredit = creditAmount.subtract(balance).max(BigDecimal.ZERO);

        List<InvoiceLine> lines = invoiceLines.findByInvoiceIdOrderByLineNumberAsc(invoiceId);
        Map<UUID, BigDecimal> deliveredByProduct = aggregateDeliveredByProductForInvoice(invoiceId);
        // Same per-line product allocation rule as createCreditNote, so the
        // preview matches the eventual BR row-for-row.
        Map<UUID, BigDecimal> productReturnedAcc = new HashMap<>();
        Map<UUID, InvoiceLine> firstLineByProduct = new HashMap<>();
        for (InvoiceLine il : lines) {
            firstLineByProduct.putIfAbsent(il.getProductId(), il);
            BigDecimal delivered = deliveredByProduct.getOrDefault(il.getProductId(), BigDecimal.ZERO);
            BigDecimal alreadyAttributed = productReturnedAcc.getOrDefault(il.getProductId(), BigDecimal.ZERO);
            BigDecimal qty = delivered.subtract(alreadyAttributed).max(BigDecimal.ZERO).min(il.getQuantity());
            if (qty.signum() > 0) {
                productReturnedAcc.merge(il.getProductId(), qty, BigDecimal::add);
            }
        }
        List<SalesDto.CreditNoteReturnLineDto> returnRows = new ArrayList<>();
        for (Map.Entry<UUID, BigDecimal> e : productReturnedAcc.entrySet()) {
            if (e.getValue().signum() <= 0) continue;
            InvoiceLine ref = firstLineByProduct.get(e.getKey());
            returnRows.add(new SalesDto.CreditNoteReturnLineDto(
                    ref.getProductId(), ref.getSnapshotName(), ref.getSnapshotSku(),
                    ref.getUomId(), e.getValue()));
        }

        return new SalesDto.CreditNotePreviewDto(
                inv.getId(), inv.getNumber(),
                inv.getPartyId(), customerName,
                inv.getCurrency(),
                inv.getTotal(), alreadyPaid, balance,
                creditAmount, toCustomerCredit,
                !returnRows.isEmpty(), returnRows,
                blockReason);
    }

    private String computeBlockReason(Invoice inv) {
        if (inv.getStatus() == InvoiceStatus.DRAFT) return "DRAFT";
        if (inv.getStatus() == InvoiceStatus.CANCELLED) return "CANCELLED";
        if (creditNotes.countNonDraftByInvoiceId(inv.getId()) > 0) return "ALREADY_CREDITED";
        return null;
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

    private SalesDto.LineDto toLineDto(InvoiceLine l) {
        return new SalesDto.LineDto(l.getId(), l.getLineNumber(), l.getProductId(), l.getUomId(),
                l.getQuantity(), l.getUnitPrice(), l.getDiscountPercent(), l.getTaxRate(),
                l.getLineTotal(), l.getSnapshotName(), l.getSnapshotSku());
    }

    private SalesDto.QuoteDto toQuoteDto(Quote q, List<QuoteLine> lines, String customerName) {
        Invoice linked = invoices.findFirstByQuoteIdOrderByCreatedAtDesc(q.getId()).orElse(null);
        return new SalesDto.QuoteDto(q.getId(), q.getNumber(), q.getPartyId(), customerName,
                q.getIssueDate(), q.getValidUntil(), q.getStatus().name(), q.getCurrency(),
                q.getSubtotal(), q.getDiscountAmount(), q.getTaxAmount(), q.getTotal(),
                q.getNotes(), lines.stream().map(this::toLineDto).toList(), q.getCreatedAt(),
                linked != null ? linked.getId() : null,
                linked != null ? linked.getNumber() : null,
                linked != null ? linked.getStatus().name() : null);
    }

    private SalesDto.InvoiceDto toInvoiceDto(Invoice inv, List<InvoiceLine> lines, String customerName) {
        return toInvoiceDto(inv, lines, customerName, 0L);
    }

    private SalesDto.InvoiceDto toInvoiceDto(Invoice inv, List<InvoiceLine> lines, String customerName, long creditNoteCount) {
        Quote linkedQuote = inv.getQuoteId() == null ? null : quotes.findById(inv.getQuoteId()).orElse(null);
        return new SalesDto.InvoiceDto(inv.getId(), inv.getNumber(), inv.getPartyId(), customerName,
                inv.getQuoteId(), inv.getIssueDate(), inv.getDueDate(), inv.getStatus().name(),
                inv.getDeliveryStatus().name(),
                inv.getCurrency(), inv.getSubtotal(), inv.getDiscountAmount(), inv.getTaxAmount(),
                inv.getTotal(), inv.getPaidAmount(), inv.getBalance(),
                inv.getPaymentTerms(), inv.getNotes(),
                lines.stream().map(this::toLineDto).toList(), inv.getCreatedAt(),
                linkedQuote != null ? linkedQuote.getNumber() : null,
                linkedQuote != null ? linkedQuote.getStatus().name() : null,
                creditNoteCount);
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
        vars.put("taxEnabled", tenantSettings.isTaxEnabled(TenantContext.require()));
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
        vars.put("taxEnabled", tenantSettings.isTaxEnabled(TenantContext.require()));
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
        vars.put("taxEnabled", tenantSettings.isTaxEnabled(TenantContext.require()));
        return vars;
    }
}
