package com.minierp.allocation.internal;

import com.minierp.allocation.api.AllocationEngine;
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
import java.util.List;
import java.util.UUID;

/**
 * Phase 1 read-only implementation of {@link AllocationEngine}. Computes open
 * items per party by querying the existing source tables (invoices, payments,
 * customer_credits, purchase_invoices) and netting against the legacy
 * allocation tables ({@code payment_allocations}, {@code customer_credit_usages})
 * plus the new {@code allocations} table.
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
        // residual. Open = amount − Σ(legacy payment_allocations.allocated)
        //                       − Σ(new allocations.amount where positive_type=PAYMENT).
        List<OpenItem> out = new ArrayList<>();
        jdbc.query("""
                SELECT p.id, p.number, p.payment_date, p.amount, p.status,
                       p.amount
                       - COALESCE((SELECT SUM(allocated_amount) FROM payment_allocations
                                   WHERE payment_id = p.id), 0)
                       - COALESCE((SELECT SUM(amount) FROM allocations
                                   WHERE positive_type = 'PAYMENT' AND positive_id = p.id), 0)
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
        // Supplier payments / refunds-received that haven't been fully allocated
        // yet. Compute residual the same way as customer payments: payment.amount
        // − Σ(payment_allocations.allocated_amount) − Σ(allocations.amount on the
        // positive side). Only CONFIRMED rows are real cash movements.
        List<OpenItem> out = new ArrayList<>();
        jdbc.query("""
                SELECT p.id, p.number, p.payment_date, p.amount, p.status,
                       p.amount
                       - COALESCE((SELECT SUM(allocated_amount) FROM payment_allocations
                                   WHERE payment_id = p.id), 0)
                       - COALESCE((SELECT SUM(amount) FROM allocations
                                   WHERE positive_type = 'SUPPLIER_PAYMENT' AND positive_id = p.id), 0)
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
