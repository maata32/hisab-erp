package com.hisaberp.allocation.internal;

import com.hisaberp.allocation.api.AllocationEngine;
import com.hisaberp.allocation.api.AllocationHistoryRow;
import com.hisaberp.allocation.api.AllocationLine;
import com.hisaberp.allocation.api.AllocationProposal;
import com.hisaberp.allocation.api.OpenItem;
import com.hisaberp.allocation.api.OpenItem.Sign;
import com.hisaberp.partner.api.CustomerCreditOperations;
import com.hisaberp.purchase.api.PurchaseInvoiceOperations;
import com.hisaberp.sales.api.InvoiceOperations;
import com.hisaberp.shared.error.BusinessException;
import com.hisaberp.shared.tenant.TenantContext;
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
    private final PurchaseInvoiceOperations purchaseInvoiceOps;
    private final CustomerCreditOperations customerCreditOps;

    // Source-type constants — also persisted into allocations.positive_type /
    // negative_type. Treat as part of the public contract.
    public static final String T_INVOICE = "INVOICE";
    public static final String T_PAYMENT = "PAYMENT";
    public static final String T_CUSTOMER_CREDIT = "CUSTOMER_CREDIT";
    public static final String T_PURCHASE_INVOICE = "PURCHASE_INVOICE";
    public static final String T_SUPPLIER_PAYMENT = "SUPPLIER_PAYMENT";
    public static final String T_CREDIT_NOTE = "CREDIT_NOTE";
    public static final String T_PURCHASE_CREDIT_NOTE = "PURCHASE_CREDIT_NOTE";

    @Override
    @Transactional(readOnly = true)
    public List<OpenItem> findOpenItemsByParty(UUID partyId) {
        UUID tenant = TenantContext.require();
        List<OpenItem> items = new ArrayList<>();
        items.addAll(openInvoices(tenant, partyId));
        items.addAll(openPayments(tenant, partyId));
        items.addAll(openCustomerCredits(tenant, partyId));
        items.addAll(openPurchaseInvoices(tenant, partyId));
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
                case T_PURCHASE_CREDIT_NOTE -> new String[]{"purchase_credit_notes", "number"};
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
    public BigDecimal apply(UUID partyId,
                            String positiveType, UUID positiveId,
                            String negativeType, UUID negativeId,
                            BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return BigDecimal.ZERO;
        List<OpenItem> items = findOpenItemsByParty(partyId);
        OpenItem pos = findOpen(items, positiveType, positiveId, Sign.POSITIVE);
        OpenItem neg = findOpen(items, negativeType, negativeId, Sign.NEGATIVE);
        if (pos == null || neg == null) {
            throw new BusinessException("error.allocation.item_not_open",
                    java.util.Map.of("positiveId", positiveId, "negativeId", negativeId));
        }
        BigDecimal capped = amount.min(pos.amountOpen()).min(neg.amountOpen());
        if (capped.signum() <= 0) return BigDecimal.ZERO;

        // Reduce each side's open balance through the owning module.
        reduceOpen(positiveType, positiveId, negativeType, capped);
        reduceOpen(negativeType, negativeId, positiveType, capped);

        allocationsRepo.save(Allocation.builder()
                .partyId(partyId)
                .positiveType(positiveType).positiveId(positiveId)
                .negativeType(negativeType).negativeId(negativeId)
                .amount(capped)
                .build());
        return capped;
    }

    private OpenItem findOpen(List<OpenItem> items, String type, UUID id, Sign sign) {
        return items.stream()
                .filter(i -> i.sourceType().equals(type) && i.sourceId().equals(id) && i.sign() == sign)
                .findFirst().orElse(null);
    }

    /** Reduce one side's open balance by {@code amount}. Invoices and credits carry
     *  a real balance to mutate through the owning module; cash payments track
     *  their residual via the {@code allocations} rows only (no balance column). */
    private void reduceOpen(String type, UUID id, String counterpartType, BigDecimal amount) {
        switch (type) {
            case T_INVOICE -> {
                // Settling by a customer credit goes through the credit path;
                // anything else (cash payment, compensation) reduces the balance.
                if (T_CUSTOMER_CREDIT.equals(counterpartType)) invoiceOps.applyCredit(id, amount);
                else invoiceOps.applyPayment(id, amount);
            }
            case T_PURCHASE_INVOICE -> purchaseInvoiceOps.applyPayment(id, amount);
            case T_CUSTOMER_CREDIT -> customerCreditOps.consumeCredit(id, amount, "Allocation");
            default -> { /* PAYMENT: residual tracked via allocations, nothing to mutate */ }
        }
    }

    @Override
    @Transactional
    public BigDecimal applyCreditToInvoice(UUID creditId, UUID invoiceId, BigDecimal amount) {
        UUID party = jdbc.queryForObject("SELECT party_id FROM customer_credits WHERE id = ?", UUID.class, creditId);
        UUID invoiceParty = jdbc.queryForObject("SELECT party_id FROM invoices WHERE id = ?", UUID.class, invoiceId);
        if (party == null || invoiceParty == null || !party.equals(invoiceParty)) {
            throw new BusinessException("error.allocation.party_mismatch",
                    java.util.Map.of("creditId", creditId, "invoiceId", invoiceId));
        }
        // Sale invoice = POSITIVE, customer credit = NEGATIVE.
        return apply(party, T_INVOICE, invoiceId, T_CUSTOMER_CREDIT, creditId, amount);
    }

    @Override
    @Transactional
    public BigDecimal applyCreditToRefund(UUID creditId, UUID refundPaymentId, BigDecimal amount) {
        UUID creditParty = jdbc.queryForObject("SELECT party_id FROM customer_credits WHERE id = ?", UUID.class, creditId);
        UUID paymentParty = jdbc.queryForObject("SELECT party_id FROM payments WHERE id = ?", UUID.class, refundPaymentId);
        if (creditParty == null || paymentParty == null || !creditParty.equals(paymentParty)) {
            throw new BusinessException("error.allocation.party_mismatch",
                    java.util.Map.of("creditId", creditId, "paymentId", refundPaymentId));
        }
        // Refund payment = CASH_OUT (POSITIVE); customer credit = NEGATIVE.
        return apply(creditParty, T_PAYMENT, refundPaymentId, T_CUSTOMER_CREDIT, creditId, amount);
    }

    @Override
    @Transactional
    public BigDecimal applySupplierRefundToRetrait(UUID refundPaymentId, UUID retraitPaymentId, BigDecimal amount) {
        UUID refundParty = jdbc.queryForObject("SELECT party_id FROM payments WHERE id = ?", UUID.class, refundPaymentId);
        UUID retraitParty = jdbc.queryForObject("SELECT party_id FROM payments WHERE id = ?", UUID.class, retraitPaymentId);
        if (refundParty == null || retraitParty == null || !refundParty.equals(retraitParty)) {
            throw new BusinessException("error.allocation.party_mismatch",
                    java.util.Map.of("refundPaymentId", refundPaymentId, "retraitPaymentId", retraitPaymentId));
        }
        // Versement = CASH_IN (NEGATIVE), retrait = CASH_OUT (POSITIVE).
        return apply(refundParty, T_PAYMENT, retraitPaymentId, T_PAYMENT, refundPaymentId, amount);
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
                  AND status NOT IN ('DRAFT','CANCELLED','PAID')
                  AND balance > 0
                """, (rs, i) -> new OpenItem(
                        T_INVOICE,
                        rs.getObject("id", UUID.class),
                        Sign.POSITIVE,
                        rs.getBigDecimal("total"),
                        rs.getBigDecimal("balance"),
                        rs.getObject("issue_date", java.time.LocalDate.class),
                        rs.getObject("due_date", java.time.LocalDate.class),
                        rs.getString("status"),
                        rs.getString("number")),
                tenant, partyId);
    }

    private List<OpenItem> openPayments(UUID tenant, UUID partyId) {
        // Unified net-position payments (party-agnostic): a CASH_IN sits on the
        // NEGATIVE side (cash received reduces what the party owes us), a CASH_OUT
        // on the POSITIVE side (cash paid out). Residual = amount − allocations
        // consuming this payment (on EITHER side, reversed excluded) − surplus
        // minted as a customer credit (customer_credits.source_payment_id) − the
        // legacy CUSTOMER_BALANCE shortcut.
        List<OpenItem> out = new ArrayList<>();
        jdbc.query("""
                SELECT p.id, p.number, p.payment_date, p.amount, p.status, p.type,
                       p.amount
                       - COALESCE((SELECT SUM(amount) FROM allocations
                                   WHERE ((positive_type = 'PAYMENT' AND positive_id = p.id)
                                       OR (negative_type = 'PAYMENT' AND negative_id = p.id))
                                     AND reversed_at IS NULL), 0)
                       - COALESCE((SELECT SUM(initial_amount) FROM customer_credits
                                   WHERE source_payment_id = p.id), 0)
                       - COALESCE((SELECT SUM(allocated_amount) FROM payment_allocations
                                   WHERE payment_id = p.id AND target_type = 'CUSTOMER_BALANCE'), 0)
                       AS amount_open
                FROM payments p
                WHERE p.tenant_id = ? AND p.party_id = ?
                  AND p.status = 'CONFIRMED'
                  AND p.type IN ('CASH_IN','CASH_OUT')
                """, (ResultSet rs) -> {
                    BigDecimal open = rs.getBigDecimal("amount_open");
                    if (open == null || open.signum() <= 0) return;
                    out.add(new OpenItem(
                            T_PAYMENT,
                            rs.getObject("id", UUID.class),
                            "CASH_IN".equals(rs.getString("type")) ? Sign.NEGATIVE : Sign.POSITIVE,
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
                        Sign.NEGATIVE,
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
}
