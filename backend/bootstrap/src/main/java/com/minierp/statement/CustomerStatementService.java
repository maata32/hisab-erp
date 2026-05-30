package com.minierp.statement;

import com.minierp.partner.api.PartnerLookup;
import com.minierp.partner.api.CustomerStatementLookup;
import com.minierp.partner.api.PartnerSummary;
import com.minierp.partner.api.StatementCreditEntry;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.payment.api.PaymentLookup;
import com.minierp.payment.api.StatementPaymentEntry;
import com.minierp.sales.api.SalesStatementLookup;
import com.minierp.sales.api.StatementCreditNoteEntry;
import com.minierp.sales.api.StatementInvoiceEntry;
import com.minierp.sales.api.StatementInvoiceLine;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.tenant.api.TenantBranding;
import com.minierp.tenant.api.TenantLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerStatementService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final PartnerLookup customers;
    private final CustomerStatementLookup statementLookup;
    private final SalesStatementLookup salesLookup;
    private final PaymentLookup paymentLookup;
    private final DocumentRenderer renderer;
    private final TenantLookup tenantLookup;

    public byte[] generate(UUID customerId, String type, LocalDate from, LocalDate to) {
        StatementType st = StatementType.parse(type);
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(2999, 12, 31);

        PartnerSummary customer = customers.findById(customerId)
                .orElseThrow(() -> NotFoundException.of("entity.customer", customerId));

        boolean detailed = st == StatementType.DETAILED;
        List<StatementInvoiceEntry> invoices = salesLookup
                .findInvoicesForStatement(customerId, effectiveFrom, effectiveTo, detailed);
        List<StatementCreditNoteEntry> creditNotes = salesLookup
                .findCreditNotesForStatement(customerId, effectiveFrom, effectiveTo);
        List<StatementPaymentEntry> payments = paymentLookup
                .findConfirmedForCustomer(customerId, effectiveFrom, effectiveTo);
        List<StatementCreditEntry> credits = statementLookup
                .findActiveCreditsForStatement(customerId, effectiveFrom, effectiveTo);
        CustomerStatementLookup.BalanceSnapshot balance = statementLookup.getBalance(customerId);

        List<Map<String, Object>> rows = (st == StatementType.OUTSTANDING)
                ? buildOutstandingRows(invoices, credits)
                : buildChronologicalRows(invoices, creditNotes, payments, credits, detailed);

        Map<String, Object> vars = new HashMap<>();
        vars.put("statementType", st.name().toLowerCase());
        vars.put("detailed", detailed);
        vars.put("outstanding", st == StatementType.OUTSTANDING);
        vars.put("statementDate", LocalDate.now());
        vars.put("periodFrom", from);
        vars.put("periodTo", to);
        vars.put("customer", Map.of(
                "code", nullSafe(customer.code()),
                "name", nullSafe(customer.name()),
                "phone", nullSafe(customer.phone()),
                "email", nullSafe(customer.email())
        ));
        BigDecimal totalPayments = payments.stream()
                .map(p -> nz(p.amount())).reduce(ZERO, BigDecimal::add);
        BigDecimal totalCreditNotes = creditNotes.stream()
                .map(cn -> nz(cn.amount())).reduce(ZERO, BigDecimal::add);
        // Sum of every still-active credit row (OVERPAYMENT, REFUND, DEPOSIT,
        // MANUAL_ADJUSTMENT). Shown as a separate "Crédit disponible" card so
        // a customer with surplus avoir / overpaid invoice sees Solde dû = 0
        // alongside Crédit disponible = X instead of a misleading single number.
        BigDecimal availableCredit = credits.stream()
                .filter(c -> "ACTIVE".equals(c.status()))
                .map(c -> nz(c.remainingAmount()))
                .reduce(ZERO, BigDecimal::add);

        Map<String, Object> balanceMap = new HashMap<>();
        balanceMap.put("totalInvoiced", nz(balance.totalInvoiced()));
        balanceMap.put("totalPaid", nz(balance.totalPaid()));
        balanceMap.put("totalPayments", totalPayments);
        balanceMap.put("totalCreditNotes", totalCreditNotes);
        balanceMap.put("balance", nz(balance.balance()));
        balanceMap.put("overdue", nz(balance.overdueAmount()));
        balanceMap.put("availableCredit", availableCredit);
        balanceMap.put("lastPaymentDate", balance.lastPaymentDate());
        vars.put("balance", balanceMap);
        vars.put("currency", nullSafe(customer.currency()));
        vars.put("rows", rows);
        TenantBranding b = tenantLookup.findBrandingById(TenantContext.require()).orElse(null);
        vars.put("orgName", b == null || b.name() == null ? "" : b.name());
        vars.put("orgAddress", b == null || b.address() == null ? "" : b.address());
        vars.put("orgPhone", b == null || b.phone() == null ? "" : b.phone());
        vars.put("orgEmail", b == null || b.email() == null ? "" : b.email());
        vars.put("logoUrl", b == null || b.logoUrl() == null ? "" : b.logoUrl());
        return renderer.renderPdf(PdfRenderRequest.of("customer-statement", vars));
    }

    /**
     * Builds the chronological list of movements with a running balance.
     * Debit = increases what the customer owes (invoices, credit usage).
     * Credit = reduces it (payments, credit notes, customer credits granted).
     */
    private List<Map<String, Object>> buildChronologicalRows(
            List<StatementInvoiceEntry> invoices,
            List<StatementCreditNoteEntry> creditNotes,
            List<StatementPaymentEntry> payments,
            List<StatementCreditEntry> credits,
            boolean detailed) {

        record Movement(LocalDate date, String kind, String number, String label,
                        BigDecimal debit, BigDecimal credit, List<StatementInvoiceLine> lines) {}

        // Map invoice → linked non-draft credit note number (one avoir per invoice
        // since the total-only refactor). Used to relabel a PAID invoice whose
        // balance was wiped out by an avoir rather than by real payments.
        Map<UUID, String> creditNoteByInvoice = new HashMap<>();
        for (var cn : creditNotes) {
            if ("DRAFT".equals(cn.status())) continue;
            creditNoteByInvoice.putIfAbsent(cn.invoiceId(), cn.number());
        }

        List<Movement> mvts = new ArrayList<>();
        for (var i : invoices) {
            String cnNumber = creditNoteByInvoice.get(i.id());
            String label = cnNumber != null
                    ? "Facture remboursée par avoir " + cnNumber
                    : "Facture " + i.status().toLowerCase();
            mvts.add(new Movement(i.issueDate(), "INVOICE", i.number(),
                    label, nz(i.total()), ZERO, detailed ? i.lines() : null));
        }
        for (var cn : creditNotes) {
            String reason = nullSafe(cn.reason()).isBlank() ? "(sans motif)" : cn.reason();
            mvts.add(new Movement(cn.issueDate(), "CREDIT_NOTE", cn.number(),
                    "Avoir : " + reason,
                    ZERO, nz(cn.amount()), null));
        }
        for (var p : payments) {
            mvts.add(new Movement(p.paymentDate(), "PAYMENT", p.number(),
                    "Paiement " + p.method() + (p.reference() != null ? " — " + p.reference() : ""),
                    ZERO, nz(p.amount()), null));
        }
        for (var c : credits) {
            // OVERPAYMENT and the legacy REFUND source are the surplus portion of
            // a movement (credit note or payment) that is ALREADY shown in full as
            // its own row above. Reading the credit as a separate row would
            // double-count it on the running balance. DEPOSIT and MANUAL_ADJUSTMENT
            // are standalone credits with no parent movement → kept.
            if ("REFUND".equals(c.source()) || "OVERPAYMENT".equals(c.source())) continue;
            LocalDate date = c.createdAt() != null
                    ? c.createdAt().atZone(ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();
            mvts.add(new Movement(date, "CREDIT", "—",
                    "Crédit accordé (" + c.source() + ")",
                    ZERO, nz(c.initialAmount()), null));
        }

        // Same-date ordering follows the business sequence: an invoice raises
        // the debt before any payment / credit can reduce it, otherwise the
        // running balance starts negative and is confusing to read.
        Map<String, Integer> kindOrder = Map.of(
                "INVOICE", 0, "PAYMENT", 1, "CREDIT_NOTE", 2, "CREDIT", 3);
        mvts.sort(Comparator.comparing(Movement::date)
                .thenComparingInt(m -> kindOrder.getOrDefault(m.kind(), 99)));

        BigDecimal running = ZERO;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var m : mvts) {
            running = running.add(m.debit()).subtract(m.credit());
            Map<String, Object> row = new HashMap<>();
            row.put("date", m.date());
            row.put("kind", m.kind());
            row.put("number", m.number());
            row.put("label", m.label());
            row.put("debit", m.debit());
            row.put("credit", m.credit());
            row.put("running", running);
            row.put("lines", (m.lines() != null && !m.lines().isEmpty())
                    ? m.lines().stream().map(l -> Map.<String, Object>of(
                            "productName", nullSafe(l.productName()),
                            "sku", nullSafe(l.sku()),
                            "quantity", nz(l.quantity()),
                            "unitPrice", nz(l.unitPrice()),
                            "discountPercent", nz(l.discountPercent()),
                            "lineTotal", nz(l.lineTotal())
                    )).toList()
                    : List.of());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Outstanding mode: only items still open at the end of the period.
     * Invoices with balance > 0 (debit side) and customer credits with remaining > 0
     * (credit side, in favour of the customer).
     */
    private List<Map<String, Object>> buildOutstandingRows(
            List<StatementInvoiceEntry> invoices,
            List<StatementCreditEntry> credits) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (var i : invoices) {
            if (nz(i.balance()).signum() <= 0) continue;
            if ("CANCELLED".equals(i.status()) || "REFUNDED".equals(i.status())) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("date", i.issueDate());
            row.put("kind", "INVOICE");
            row.put("number", i.number());
            row.put("label", "Facture impayée — éch. " + (i.dueDate() != null ? i.dueDate() : "—"));
            row.put("debit", nz(i.balance()));
            row.put("credit", ZERO);
            row.put("running", null);
            rows.add(row);
        }
        for (var c : credits) {
            if (nz(c.remainingAmount()).signum() <= 0) continue;
            LocalDate date = c.createdAt() != null
                    ? c.createdAt().atZone(ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();
            Map<String, Object> row = new HashMap<>();
            row.put("date", date);
            row.put("kind", "CREDIT");
            row.put("number", "—");
            row.put("label", "Crédit en faveur du client (" + c.source() + ")");
            row.put("debit", ZERO);
            row.put("credit", nz(c.remainingAmount()));
            row.put("running", null);
            rows.add(row);
        }
        rows.sort(Comparator.<Map<String, Object>, LocalDate>comparing(
                r -> (LocalDate) r.get("date")));
        return rows;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : ZERO;
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    private enum StatementType {
        FULL, DETAILED, OUTSTANDING;
        static StatementType parse(String s) {
            if (s == null) return FULL;
            return switch (s.toLowerCase()) {
                case "detailed" -> DETAILED;
                case "outstanding" -> OUTSTANDING;
                default -> FULL;
            };
        }
    }
}
