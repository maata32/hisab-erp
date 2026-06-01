package com.minierp.allocation.internal;

import com.minierp.allocation.api.AllocationEngine;
import com.minierp.allocation.api.AllocationHistoryRow;
import com.minierp.allocation.api.AllocationLine;
import com.minierp.allocation.api.AllocationProposal;
import com.minierp.allocation.api.OpenItem;
import com.minierp.allocation.api.OpenItem.Sign;
import com.minierp.partner.api.CustomerCreditOperations;
import com.minierp.sales.api.InvoiceOperations;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only implementation of {@link AllocationEngine}. Computes open items per
 * party by querying the source-of-truth tables (invoices, payments,
 * customer_credits, purchase_invoices) and netting against the unified
 * {@code allocations} table.
 *
 * <p>The {@code GREATEST(legacy, new)} transition hack is gone (#4): the
 * {@code allocations} table is authoritative for the invoice-settling part of a
 * payment's residual. The legacy {@code payment_allocations} table is now read
 * only for the one surplus path it modeled but {@code allocations} never did —
 * the {@code CUSTOMER_BALANCE} shortcut (dead in the current UI, kept for
 * historical rows). {@code customer_credit_usages} is no longer read at all
 * (credit residual is the authoritative {@code customer_credits.remaining_amount}).</p>
 *
 * <p>Customer side only for Phase 1. Supplier side (purchase invoices,
 * supplier payments) will land alongside the Phase 2 UI refactor.</p>
 */
@Service
@RequiredArgsConstructor
class AllocationEngineImpl implements AllocationEngine {

    private final JdbcTemplate jdbc;
    private final AllocationRepository allocationsRepo;
    private final InvoiceOperations invoiceOps;
    private final CustomerCreditOperations customerCreditOps;

    // Source-type constants — also persisted into allocations.positive_type /
    // negative_type. Treat as part of the public contract.
    public static final String T_INVOICE = "INVOICE";
    public static final String T_PAYMENT = "PAYMENT";
    public static final String T_CUSTOMER_CREDIT = "CUSTOMER_CREDIT";
    public static final String T_PURCHASE_INVOICE = "PURCHASE_INVOICE";
    public static final String T_SUPPLIER_PAYMENT = "SUPPLIER_PAYMENT";
    public static final String T_CREDIT_NOTE = "CREDIT_NOTE";

    @Override
    @Transactional(readOnly = true)
    public List<OpenItem> findOpenItemsByParty(UUID partyId) {
        UUID tenant = TenantContext.require();
        List<OpenItem> items = new ArrayList<>();
        items.addAll(openInvoices(tenant, partyId));
        items.addAll(openPayments(tenant, partyId));
        items.addAll(openCustomerCredits(tenant, partyId));
        items.addAll(openPurchaseInvoices(tenant, partyId));
        items.addAll(openSupplierPayments(tenant, partyId));
        items.sort(Comparator.comparing(OpenItem::dateRef));
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllocationHistoryRow> findAllocationHistoryByParty(UUID partyId) {
        UUID tenant = TenantContext.require();
        // Raw audit rows, newest first. Labels are resolved in a second pass so
        // a reversed row still shows what it paired even after the fact.
        List<Object[]> raw = new ArrayList<>();
        jdbc.query("""
                SELECT id, positive_type, positive_id, negative_type, negative_id,
                       amount, allocated_at, notes, reversed_at, reversal_reason
                FROM allocations
                WHERE tenant_id = ? AND party_id = ?
                ORDER BY allocated_at DESC, id DESC
                """, (ResultSet rs) -> {
                    raw.add(new Object[]{
                            rs.getObject("id", UUID.class),
                            rs.getString("positive_type"),
                            rs.getObject("positive_id", UUID.class),
                            rs.getString("negative_type"),
                            rs.getObject("negative_id", UUID.class),
                            rs.getBigDecimal("amount"),
                            toInstant(rs.getTimestamp("allocated_at")),
                            rs.getString("notes"),
                            toInstant(rs.getTimestamp("reversed_at")),
                            rs.getString("reversal_reason")});
                }, tenant, partyId);
        if (raw.isEmpty()) return List.of();

        LabelResolver labels = new LabelResolver(tenant);
        for (Object[] r : raw) {
            labels.note((String) r[1], (UUID) r[2]);
            labels.note((String) r[3], (UUID) r[4]);
        }
        labels.resolve();

        List<AllocationHistoryRow> out = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            out.add(new AllocationHistoryRow(
                    (UUID) r[0],
                    (String) r[1], (UUID) r[2], labels.labelOf((String) r[1], (UUID) r[2]),
                    (String) r[3], (UUID) r[4], labels.labelOf((String) r[3], (UUID) r[4]),
                    (BigDecimal) r[5],
                    (java.time.Instant) r[6],
                    (String) r[7],
                    (java.time.Instant) r[8],
                    (String) r[9]));
        }
        return out;
    }

    /**
     * Batches label lookups per source-type so the history query stays O(types)
     * instead of O(rows). Each engine source-type maps to a (table, label-column)
     * pair; ids are collected first, then resolved in one IN-query per type.
     */
    private final class LabelResolver {
        private final UUID tenant;
        private final Map<String, java.util.Set<UUID>> idsByType = new HashMap<>();
        private final Map<String, String> resolved = new HashMap<>();

        LabelResolver(UUID tenant) { this.tenant = tenant; }

        void note(String type, UUID id) {
            if (type == null || id == null) return;
            idsByType.computeIfAbsent(type, k -> new java.util.HashSet<>()).add(id);
        }

        void resolve() {
            for (var e : idsByType.entrySet()) {
                String[] tc = tableAndColumn(e.getKey());
                if (tc == null) continue;
                List<UUID> ids = new ArrayList<>(e.getValue());
                String ph = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
                List<Object> args = new ArrayList<>(ids);
                args.add(tenant);
                jdbc.query("SELECT id, " + tc[1] + " AS label FROM " + tc[0]
                                + " WHERE id IN (" + ph + ") AND tenant_id = ?",
                        (ResultSet rs) -> {
                            // Void block → RowCallbackHandler (per-row, cursor positioned);
                            // an expression lambda here would bind to ResultSetExtractor.
                            resolved.put(
                                    key(e.getKey(), rs.getObject("id", UUID.class)),
                                    rs.getString("label"));
                        },
                        args.toArray());
            }
        }

        String labelOf(String type, UUID id) {
            String label = resolved.get(key(type, id));
            // Fallback to a short id when the source row is gone or unmapped.
            return label != null ? label : (id == null ? "?" : id.toString().substring(0, 8));
        }

        private String key(String type, UUID id) { return type + ":" + id; }

        private String[] tableAndColumn(String type) {
            return switch (type) {
                case T_INVOICE -> new String[]{"invoices", "number"};
                case T_PURCHASE_INVOICE -> new String[]{"purchase_invoices", "number"};
                case T_PAYMENT, T_SUPPLIER_PAYMENT -> new String[]{"payments", "number"};
                case T_CUSTOMER_CREDIT -> new String[]{"customer_credits", "source"};
                case T_CREDIT_NOTE -> new String[]{"credit_notes", "number"};
                default -> null;
            };
        }
    }

    private static java.time.Instant toInstant(java.sql.Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    @Override
    @Transactional(readOnly = true)
    public AllocationProposal propose(UUID partyId, String sourceType, UUID sourceId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return new AllocationProposal(List.of(), BigDecimal.ZERO);
        }
        List<OpenItem> items = findOpenItemsByParty(partyId);
        OpenItem source = items.stream()
                .filter(i -> i.sourceType().equals(sourceType) && i.sourceId().equals(sourceId))
                .findFirst()
                .orElse(null);
        if (source == null) {
            return new AllocationProposal(List.of(), amount);
        }
        Sign opposite = source.sign() == Sign.POSITIVE ? Sign.NEGATIVE : Sign.POSITIVE;
        List<OpenItem> candidates = items.stream()
                .filter(i -> i.sign() == opposite)
                .sorted(Comparator.comparing(OpenItem::dateRef))
                .toList();

        List<AllocationLine> lines = new ArrayList<>();
        BigDecimal remaining = amount;
        for (OpenItem c : candidates) {
            if (remaining.signum() <= 0) break;
            BigDecimal take = remaining.min(c.amountOpen());
            if (take.signum() <= 0) continue;
            OpenItem positive = source.sign() == Sign.POSITIVE ? source : c;
            OpenItem negative = source.sign() == Sign.POSITIVE ? c : source;
            lines.add(new AllocationLine(positive, negative, take));
            remaining = remaining.subtract(take);
        }
        return new AllocationProposal(lines, remaining);
    }

    @Override
    @Transactional
    public BigDecimal applyCreditToInvoice(UUID creditId, UUID invoiceId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        // Resolve the credit's owning party so we can persist allocations.party_id
        // and so the engine can sanity-check that the invoice belongs to the
        // same party before mutating either row.
        UUID partyId = jdbc.queryForObject(
                "SELECT party_id FROM customer_credits WHERE id = ?", UUID.class, creditId);
        UUID invoiceParty = jdbc.queryForObject(
                "SELECT party_id FROM invoices WHERE id = ?", UUID.class, invoiceId);
        if (partyId == null || invoiceParty == null || !partyId.equals(invoiceParty)) {
            throw new BusinessException("error.allocation.party_mismatch",
                    java.util.Map.of("creditId", creditId, "invoiceId", invoiceId));
        }

        // invoiceOps.applyCredit caps to invoice.balance and returns the actual
        // amount imputed; customerCreditOps.consumeCredit caps to credit.remaining.
        // We apply the engine's "amount" through both so the audit row reflects
        // the true min(invoice.balance, credit.remaining, amount).
        BigDecimal imputed = invoiceOps.applyCredit(invoiceId, amount);
        if (imputed.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal consumed = customerCreditOps.consumeCredit(creditId, imputed,
                "Applied to invoice " + invoiceId);

        allocationsRepo.save(Allocation.builder()
                .partyId(partyId)
                .positiveType(T_CUSTOMER_CREDIT)
                .positiveId(creditId)
                .negativeType(T_INVOICE)
                .negativeId(invoiceId)
                .amount(consumed)
                .build());
        return consumed;
    }

    @Override
    @Transactional
    public BigDecimal applyCreditToRefund(UUID creditId, UUID refundPaymentId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        UUID creditParty = jdbc.queryForObject(
                "SELECT party_id FROM customer_credits WHERE id = ?", UUID.class, creditId);
        // Verify the target payment is a CUSTOMER_REFUND and belongs to the
        // same party; reject anything else (REFUND of a CONFIRMED CUSTOMER_PAYMENT
        // is handled by the legacy {@code payment.refund} flow, not here).
        var paymentMeta = jdbc.queryForMap(
                "SELECT party_id, type, amount FROM payments WHERE id = ?", refundPaymentId);
        UUID paymentParty = (UUID) paymentMeta.get("party_id");
        String paymentType = (String) paymentMeta.get("type");
        java.math.BigDecimal paymentAmount = (java.math.BigDecimal) paymentMeta.get("amount");
        if (creditParty == null || paymentParty == null || !creditParty.equals(paymentParty)) {
            throw new BusinessException("error.allocation.party_mismatch",
                    java.util.Map.of("creditId", creditId, "paymentId", refundPaymentId));
        }
        if (!"CUSTOMER_REFUND".equals(paymentType)) {
            throw new BusinessException("error.allocation.not_a_refund",
                    java.util.Map.of("paymentId", refundPaymentId, "type", paymentType));
        }
        // Cap to the payment's own amount so we never claim more than the
        // refund actually paid out.
        BigDecimal cappedToPayment = amount.min(paymentAmount);
        BigDecimal consumed = customerCreditOps.consumeCredit(creditId, cappedToPayment,
                "Settled by refund payment " + refundPaymentId);

        allocationsRepo.save(Allocation.builder()
                .partyId(creditParty)
                .positiveType(T_CUSTOMER_CREDIT)
                .positiveId(creditId)
                .negativeType(T_PAYMENT)
                .negativeId(refundPaymentId)
                .amount(consumed)
                .build());
        return consumed;
    }

    @Override
    @Transactional
    public BigDecimal applySupplierRefundToRetrait(UUID refundPaymentId, UUID retraitPaymentId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        var refundMeta = jdbc.queryForMap(
                "SELECT party_id, type, amount FROM payments WHERE id = ?", refundPaymentId);
        var retraitMeta = jdbc.queryForMap(
                "SELECT party_id, type, amount FROM payments WHERE id = ?", retraitPaymentId);
        UUID refundParty = (UUID) refundMeta.get("party_id");
        UUID retraitParty = (UUID) retraitMeta.get("party_id");
        if (refundParty == null || retraitParty == null || !refundParty.equals(retraitParty)) {
            throw new BusinessException("error.allocation.party_mismatch",
                    java.util.Map.of("refundPaymentId", refundPaymentId, "retraitPaymentId", retraitPaymentId));
        }
        if (!"SUPPLIER_REFUND".equals(refundMeta.get("type"))) {
            throw new BusinessException("error.allocation.not_a_supplier_refund",
                    java.util.Map.of("paymentId", refundPaymentId, "type", String.valueOf(refundMeta.get("type"))));
        }
        if (!"SUPPLIER_PAYMENT".equals(retraitMeta.get("type"))) {
            throw new BusinessException("error.allocation.not_a_supplier_retrait",
                    java.util.Map.of("paymentId", retraitPaymentId, "type", String.valueOf(retraitMeta.get("type"))));
        }
        BigDecimal refundAmount = (BigDecimal) refundMeta.get("amount");
        BigDecimal retraitAmount = (BigDecimal) retraitMeta.get("amount");
        // Open residual of the versement = its amount minus what it has already
        // been imputed on (positive-side rows in `allocations`, reversed excluded).
        BigDecimal refundOpen = refundAmount.subtract(
                allocationsRepo.sumByPositive(T_SUPPLIER_PAYMENT, refundPaymentId));
        BigDecimal consumed = amount.min(refundOpen).min(retraitAmount);
        if (consumed.signum() <= 0) return BigDecimal.ZERO;

        allocationsRepo.save(Allocation.builder()
                .partyId(refundParty)
                .positiveType(T_SUPPLIER_PAYMENT)
                .positiveId(refundPaymentId)
                .negativeType(T_SUPPLIER_PAYMENT)
                .negativeId(retraitPaymentId)
                .amount(consumed)
                .build());
        return consumed;
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    private List<OpenItem> openInvoices(UUID tenant, UUID partyId) {
        // Customer invoices still owed: status active and balance > 0. The
        // balance column is authoritative (kept in sync by applyPayment /
        // applyCredit), so we don't subtract allocation rows here.
        return jdbc.query("""
                SELECT id, number, issue_date, due_date, total, balance, status
                FROM invoices
                WHERE tenant_id = ? AND party_id = ?
                  AND status NOT IN ('DRAFT','CANCELLED','PAID','REFUNDED')
                  AND balance > 0
                """, (rs, i) -> new OpenItem(
                        T_INVOICE,
                        rs.getObject("id", UUID.class),
                        Sign.NEGATIVE,
                        rs.getBigDecimal("total"),
                        rs.getBigDecimal("balance"),
                        rs.getObject("issue_date", java.time.LocalDate.class),
                        rs.getObject("due_date", java.time.LocalDate.class),
                        rs.getString("status"),
                        rs.getString("number")),
                tenant, partyId);
    }

    private List<OpenItem> openPayments(UUID tenant, UUID partyId) {
        // Customer payments that brought cash in and still have unallocated
        // residual. The unified `allocations` table is now authoritative for the
        // invoice-settling part (kept in sync by the Phase-5 double-write, the
        // 0052 backfill, and the #1 refund soft-void — reversed rows excluded).
        //
        // A payment's amount is consumed by exactly three things, each read from
        // its own source of truth — no GREATEST hack, no double-count:
        //   1. invoice-settling allocations (active rows in `allocations`);
        //   2. surplus routed to a customer credit, materialized on
        //      customer_credits.source_payment_id (initial_amount is immutable,
        //      so it reflects what the payment minted even after the credit is
        //      spent);
        //   3. the legacy CUSTOMER_BALANCE surplus shortcut — the only path not
        //      modeled in `allocations`. Dead in the current UI (surplus now
        //      always goes to CUSTOMER_CREDIT) but kept here so historical rows
        //      don't regress.
        List<OpenItem> out = new ArrayList<>();
        jdbc.query("""
                SELECT p.id, p.number, p.payment_date, p.amount, p.status,
                       p.amount
                       - COALESCE((SELECT SUM(amount) FROM allocations
                                   WHERE positive_type = 'PAYMENT' AND positive_id = p.id
                                     AND reversed_at IS NULL), 0)
                       - COALESCE((SELECT SUM(initial_amount) FROM customer_credits
                                   WHERE source_payment_id = p.id), 0)
                       - COALESCE((SELECT SUM(allocated_amount) FROM payment_allocations
                                   WHERE payment_id = p.id AND target_type = 'CUSTOMER_BALANCE'), 0)
                       AS amount_open
                FROM payments p
                WHERE p.tenant_id = ? AND p.party_id = ?
                  AND p.status = 'CONFIRMED'
                  AND p.type IN ('CUSTOMER_PAYMENT','CUSTOMER_DEPOSIT')
                """, (ResultSet rs) -> {
                    BigDecimal open = rs.getBigDecimal("amount_open");
                    if (open == null || open.signum() <= 0) return;
                    out.add(new OpenItem(
                            T_PAYMENT,
                            rs.getObject("id", UUID.class),
                            Sign.POSITIVE,
                            rs.getBigDecimal("amount"),
                            open,
                            rs.getObject("payment_date", java.time.LocalDate.class),
                            null,
                            rs.getString("status"),
                            rs.getString("number")));
                }, tenant, partyId);
        return out;
    }

    private List<OpenItem> openCustomerCredits(UUID tenant, UUID partyId) {
        return jdbc.query("""
                SELECT id, source, initial_amount, remaining_amount,
                       created_at::date AS dt, status
                FROM customer_credits
                WHERE tenant_id = ? AND party_id = ?
                  AND status = 'ACTIVE'
                  AND remaining_amount > 0
                """, (rs, i) -> new OpenItem(
                        T_CUSTOMER_CREDIT,
                        rs.getObject("id", UUID.class),
                        Sign.POSITIVE,
                        rs.getBigDecimal("initial_amount"),
                        rs.getBigDecimal("remaining_amount"),
                        rs.getObject("dt", java.time.LocalDate.class),
                        null,
                        rs.getString("status"),
                        rs.getString("source")),
                tenant, partyId);
    }

    private List<OpenItem> openPurchaseInvoices(UUID tenant, UUID partyId) {
        // Supplier invoices we still owe. From the operator's perspective these
        // are positive cash-out commitments — i.e. settling them consumes a
        // supplier payment. They sit on the NEGATIVE side of the allocation,
        // mirroring customer invoices.
        return jdbc.query("""
                SELECT id, number, invoice_date, due_date, total, balance, status
                FROM purchase_invoices
                WHERE tenant_id = ? AND party_id = ?
                  AND status NOT IN ('DRAFT','CANCELLED','PAID')
                  AND balance > 0
                """, (rs, i) -> new OpenItem(
                        T_PURCHASE_INVOICE,
                        rs.getObject("id", UUID.class),
                        Sign.NEGATIVE,
                        rs.getBigDecimal("total"),
                        rs.getBigDecimal("balance"),
                        rs.getObject("invoice_date", java.time.LocalDate.class),
                        rs.getObject("due_date", java.time.LocalDate.class),
                        rs.getString("status"),
                        rs.getString("number")),
                tenant, partyId);
    }

    private List<OpenItem> openSupplierPayments(UUID tenant, UUID partyId) {
        // Mirror of openPayments, minus the customer-credit term: supplier
        // payments never mint credits (grantCredit is customer-only). The amount
        // is consumed by (1) invoice-settling allocations (authoritative in the
        // `allocations` table) and (2) the legacy CUSTOMER_BALANCE → supplier
        // balance surplus shortcut, the only path not modeled in `allocations`.
        // Only CONFIRMED rows are real cash movements.
        List<OpenItem> out = new ArrayList<>();
        jdbc.query("""
                SELECT p.id, p.number, p.payment_date, p.amount, p.status,
                       p.amount
                       - COALESCE((SELECT SUM(amount) FROM allocations
                                   WHERE positive_type = 'SUPPLIER_PAYMENT' AND positive_id = p.id
                                     AND reversed_at IS NULL), 0)
                       - COALESCE((SELECT SUM(allocated_amount) FROM payment_allocations
                                   WHERE payment_id = p.id AND target_type = 'CUSTOMER_BALANCE'), 0)
                       AS amount_open
                FROM payments p
                WHERE p.tenant_id = ? AND p.party_id = ?
                  AND p.status = 'CONFIRMED'
                  AND p.type IN ('SUPPLIER_PAYMENT','SUPPLIER_REFUND')
                """, (ResultSet rs) -> {
                    BigDecimal open = rs.getBigDecimal("amount_open");
                    if (open == null || open.signum() <= 0) return;
                    out.add(new OpenItem(
                            T_SUPPLIER_PAYMENT,
                            rs.getObject("id", UUID.class),
                            Sign.POSITIVE,
                            rs.getBigDecimal("amount"),
                            open,
                            rs.getObject("payment_date", java.time.LocalDate.class),
                            null,
                            rs.getString("status"),
                            rs.getString("number")));
                }, tenant, partyId);
        return out;
    }
}
