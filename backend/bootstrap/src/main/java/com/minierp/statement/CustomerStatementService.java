package com.minierp.statement;

import com.minierp.customer.api.CustomerLookup;
import com.minierp.customer.api.CustomerStatementLookup;
import com.minierp.customer.api.CustomerSummary;
import com.minierp.customer.api.StatementCreditEntry;
import com.minierp.document.api.DocumentRenderer;
import com.minierp.document.api.PdfRenderRequest;
import com.minierp.payment.api.PaymentLookup;
import com.minierp.payment.api.StatementPaymentEntry;
import com.minierp.sales.api.SalesStatementLookup;
import com.minierp.sales.api.StatementCreditNoteEntry;
import com.minierp.sales.api.StatementInvoiceEntry;
import com.minierp.sales.api.StatementInvoiceLine;
import com.minierp.shared.error.NotFoundException;
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

    private final CustomerLookup customers;
    private final CustomerStatementLookup statementLookup;
    private final SalesStatementLookup salesLookup;
    private final PaymentLookup paymentLookup;
    private final DocumentRenderer renderer;

    public byte[] generate(UUID customerId, String type, LocalDate from, LocalDate to) {
        StatementType st = StatementType.parse(type);
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(2999, 12, 31);

        CustomerSummary customer = customers.findById(customerId)
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
        Map<String, Object> balanceMap = new HashMap<>();
        balanceMap.put("totalInvoiced", nz(balance.totalInvoiced()));
        balanceMap.put("totalPaid", nz(balance.totalPaid()));
        balanceMap.put("balance", nz(balance.balance()));
        balanceMap.put("overdue", nz(balance.overdueAmount()));
        balanceMap.put("lastPaymentDate", balance.lastPaymentDate());
        vars.put("balance", balanceMap);
        vars.put("currency", nullSafe(customer.currency()));
        vars.put("rows", rows);
        vars.put("orgName", "Mini-ERP");
        vars.put("orgAddress", "");
        vars.put("orgPhone", "");
        vars.put("logoUrl", "");
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

        List<Movement> mvts = new ArrayList<>();
        for (var i : invoices) {
            mvts.add(new Movement(i.issueDate(), "INVOICE", i.number(),
                    "Facture " + i.status().toLowerCase(),
                    nz(i.total()), ZERO, detailed ? i.lines() : null));
        }
        for (var cn : creditNotes) {
            mvts.add(new Movement(cn.issueDate(), "CREDIT_NOTE", cn.number(),
                    "Avoir : " + nullSafe(cn.reason()),
                    ZERO, nz(cn.amount()), null));
        }
        for (var p : payments) {
            mvts.add(new Movement(p.paymentDate(), "PAYMENT", p.number(),
                    "Paiement " + p.method() + (p.reference() != null ? " — " + p.reference() : ""),
                    ZERO, nz(p.amount()), null));
        }
        for (var c : credits) {
            LocalDate date = c.createdAt() != null
                    ? c.createdAt().atZone(ZoneId.systemDefault()).toLocalDate()
                    : LocalDate.now();
            mvts.add(new Movement(date, "CREDIT", "—",
                    "Crédit accordé (" + c.source() + ")",
                    ZERO, nz(c.initialAmount()), null));
        }

        mvts.sort(Comparator.comparing(Movement::date)
                .thenComparing(Movement::kind));

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
            if ("CANCELLED".equals(i.status())) continue;
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
