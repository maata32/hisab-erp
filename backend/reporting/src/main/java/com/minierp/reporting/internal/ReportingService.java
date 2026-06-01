package com.minierp.reporting.internal;

import com.minierp.reporting.api.ReportingDto;
import com.minierp.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<ReportingDto.DirectionRow> direction(LocalDate from, LocalDate to) {
        UUID tenant = TenantContext.require();
        String plain = """
                SELECT sale_day, sale_month, sale_year::int, sale_count, revenue, gross_profit
                FROM v_report_direction
                WHERE tenant_id = ?
                  AND (?::date IS NULL OR sale_day >= ?::date)
                  AND (?::date IS NULL OR sale_day <= ?::date)
                ORDER BY sale_day DESC
                """;
        return jdbc.query(plain, (rs, i) -> new ReportingDto.DirectionRow(
                rs.getObject("sale_day", LocalDate.class),
                rs.getObject("sale_month", LocalDate.class),
                rs.getInt("sale_year"),
                rs.getLong("sale_count"),
                rs.getBigDecimal("revenue"),
                rs.getBigDecimal("gross_profit")
        ), tenant, from, from, to, to);
    }

    @Transactional(readOnly = true)
    public List<ReportingDto.CaisseRow> caisse(LocalDate from, LocalDate to) {
        UUID tenant = TenantContext.require();
        return jdbc.query("""
                SELECT cashier_user_id, register_id, sale_day, payment_method,
                       sale_count, total_revenue, avg_ticket
                FROM v_report_caisse
                WHERE tenant_id = ?
                  AND (?::date IS NULL OR sale_day >= ?::date)
                  AND (?::date IS NULL OR sale_day <= ?::date)
                ORDER BY sale_day DESC, total_revenue DESC
                """, (rs, i) -> new ReportingDto.CaisseRow(
                rs.getObject("cashier_user_id", UUID.class),
                rs.getObject("register_id", UUID.class),
                rs.getObject("sale_day", LocalDate.class),
                rs.getString("payment_method"),
                rs.getLong("sale_count"),
                rs.getBigDecimal("total_revenue"),
                rs.getBigDecimal("avg_ticket")
        ), tenant, from, from, to, to);
    }

    @Transactional(readOnly = true)
    public List<ReportingDto.StockRow> stock(UUID warehouseId) {
        UUID tenant = TenantContext.require();
        return jdbc.query("""
                SELECT warehouse_id, product_id, product_name, sku,
                       qty_on_hand, qty_reserved, qty_available,
                       average_cost, stock_value, is_low_stock
                FROM v_report_stock
                WHERE tenant_id = ?
                  AND (?::uuid IS NULL OR warehouse_id = ?::uuid)
                ORDER BY is_low_stock DESC, product_name
                """, (rs, i) -> new ReportingDto.StockRow(
                rs.getObject("warehouse_id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getString("product_name"),
                rs.getString("sku"),
                rs.getBigDecimal("qty_on_hand"),
                rs.getBigDecimal("qty_reserved"),
                rs.getBigDecimal("qty_available"),
                rs.getBigDecimal("average_cost"),
                rs.getBigDecimal("stock_value"),
                rs.getBoolean("is_low_stock")
        ), tenant, warehouseId, warehouseId);
    }

    @Transactional(readOnly = true)
    public List<ReportingDto.ExpiryRow> expiry(Integer maxDays) {
        UUID tenant = TenantContext.require();
        return jdbc.query("""
                SELECT lot_id, lot_number, product_id, product_name, warehouse_id,
                       expiration_date, days_remaining, quantity_remaining, status, risk_level
                FROM v_report_expiry
                WHERE tenant_id = ?
                  AND (?::int IS NULL OR days_remaining <= ?::int)
                ORDER BY days_remaining ASC
                """, (rs, i) -> new ReportingDto.ExpiryRow(
                rs.getObject("lot_id", UUID.class),
                rs.getString("lot_number"),
                rs.getObject("product_id", UUID.class),
                rs.getString("product_name"),
                rs.getObject("warehouse_id", UUID.class),
                rs.getObject("expiration_date", LocalDate.class),
                rs.getInt("days_remaining"),
                rs.getBigDecimal("quantity_remaining"),
                rs.getString("status"),
                rs.getString("risk_level")
        ), tenant, maxDays, maxDays);
    }

    @Transactional(readOnly = true)
    public List<ReportingDto.PaymentRow> payments(LocalDate from, LocalDate to) {
        UUID tenant = TenantContext.require();
        return jdbc.query("""
                SELECT payment_day, payment_month, type, method,
                       payment_count, total_amount
                FROM v_report_payments
                WHERE tenant_id = ?
                  AND (?::date IS NULL OR payment_day >= ?::date)
                  AND (?::date IS NULL OR payment_day <= ?::date)
                ORDER BY payment_day DESC
                """, (rs, i) -> new ReportingDto.PaymentRow(
                rs.getObject("payment_day", LocalDate.class),
                rs.getObject("payment_month", LocalDate.class),
                rs.getString("type"),
                rs.getString("method"),
                rs.getLong("payment_count"),
                rs.getBigDecimal("total_amount")
        ), tenant, from, from, to, to);
    }

    @Transactional(readOnly = true)
    public List<ReportingDto.DeliveryRow> deliveries() {
        UUID tenant = TenantContext.require();
        return jdbc.query("""
                SELECT status, delivery_count, on_time_count, late_count
                FROM v_report_deliveries
                WHERE tenant_id = ?
                ORDER BY status
                """, (rs, i) -> new ReportingDto.DeliveryRow(
                rs.getString("status"),
                rs.getLong("delivery_count"),
                rs.getLong("on_time_count"),
                rs.getLong("late_count")
        ), tenant);
    }

    /** CDC §15.4 — stock value at risk aggregated by risk level (CRITICAL/HIGH/MEDIUM/LOW). */
    @Transactional(readOnly = true)
    public List<ReportingDto.ExpiryRiskRow> expiryRisk(UUID warehouseId) {
        UUID tenant = TenantContext.require();
        return jdbc.query("""
                SELECT e.risk_level,
                       COUNT(*)                                          AS lot_count,
                       SUM(e.quantity_remaining)                         AS qty_at_risk,
                       SUM(e.quantity_remaining * COALESCE(s.average_cost, 0)) AS value_at_risk
                FROM v_report_expiry e
                LEFT JOIN stocks s
                       ON s.product_id = e.product_id
                      AND s.warehouse_id = e.warehouse_id
                WHERE e.tenant_id = ?
                  AND (?::uuid IS NULL OR e.warehouse_id = ?::uuid)
                GROUP BY e.risk_level
                ORDER BY CASE e.risk_level
                            WHEN 'CRITICAL' THEN 1
                            WHEN 'HIGH'     THEN 2
                            WHEN 'MEDIUM'   THEN 3
                            ELSE 4
                         END
                """, (rs, i) -> new ReportingDto.ExpiryRiskRow(
                rs.getString("risk_level"),
                rs.getLong("lot_count"),
                rs.getBigDecimal("qty_at_risk"),
                rs.getBigDecimal("value_at_risk")
        ), tenant, warehouseId, warehouseId);
    }

    /** CDC §15.4 — customer aging report (balance bucketed by overdue age in days). */
    @Transactional(readOnly = true)
    public List<ReportingDto.AgingRow> aging() {
        UUID tenant = TenantContext.require();
        return jdbc.query("""
                SELECT c.id            AS customer_id,
                       c.code          AS customer_code,
                       c.name          AS customer_name,
                       COALESCE(SUM(CASE WHEN i.due_date IS NULL OR i.due_date >= CURRENT_DATE
                                         THEN i.balance ELSE 0 END), 0) AS current_balance,
                       COALESCE(SUM(CASE WHEN i.due_date < CURRENT_DATE
                                         AND (CURRENT_DATE - i.due_date) BETWEEN 1 AND 30
                                         THEN i.balance ELSE 0 END), 0) AS d1to30,
                       COALESCE(SUM(CASE WHEN i.due_date < CURRENT_DATE
                                         AND (CURRENT_DATE - i.due_date) BETWEEN 31 AND 60
                                         THEN i.balance ELSE 0 END), 0) AS d31to60,
                       COALESCE(SUM(CASE WHEN i.due_date < CURRENT_DATE
                                         AND (CURRENT_DATE - i.due_date) BETWEEN 61 AND 90
                                         THEN i.balance ELSE 0 END), 0) AS d61to90,
                       COALESCE(SUM(CASE WHEN i.due_date < CURRENT_DATE
                                         AND (CURRENT_DATE - i.due_date) > 90
                                         THEN i.balance ELSE 0 END), 0) AS d90plus,
                       COALESCE(SUM(i.balance), 0)                       AS total_outstanding
                FROM parties c
                LEFT JOIN invoices i
                       ON i.party_id = c.id
                      AND i.tenant_id = c.tenant_id
                      AND i.balance > 0
                      AND i.status NOT IN ('CANCELLED','PAID')
                WHERE c.tenant_id = ? AND c.is_customer = TRUE
                GROUP BY c.id, c.code, c.name
                HAVING COALESCE(SUM(i.balance), 0) > 0
                ORDER BY total_outstanding DESC
                """, (rs, i) -> new ReportingDto.AgingRow(
                rs.getObject("customer_id", UUID.class),
                rs.getString("customer_code"),
                rs.getString("customer_name"),
                rs.getBigDecimal("current_balance"),
                rs.getBigDecimal("d1to30"),
                rs.getBigDecimal("d31to60"),
                rs.getBigDecimal("d61to90"),
                rs.getBigDecimal("d90plus"),
                rs.getBigDecimal("total_outstanding")
        ), tenant);
    }

    @Transactional(readOnly = true)
    public List<ReportingDto.TopProductRow> topProducts(LocalDate month, int limit) {
        UUID tenant = TenantContext.require();
        return jdbc.query("""
                SELECT product_id, product_name, sku, sale_month, qty_sold, revenue
                FROM v_report_top_products
                WHERE tenant_id = ?
                  AND (?::date IS NULL OR sale_month = ?::date)
                ORDER BY revenue DESC
                LIMIT ?
                """, (rs, i) -> new ReportingDto.TopProductRow(
                rs.getObject("product_id", UUID.class),
                rs.getString("product_name"),
                rs.getString("sku"),
                rs.getObject("sale_month", LocalDate.class),
                rs.getBigDecimal("qty_sold"),
                rs.getBigDecimal("revenue")
        ), tenant, month, month, limit);
    }

    /** Snapshot of dashboard KPIs in a single round trip. */
    @Transactional(readOnly = true)
    public ReportingDto.DashboardKpis dashboardKpis() {
        UUID tenant = TenantContext.require();
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate sevenDaysAgo = today.minusDays(6);

        // ── Finance — today/month sales ───────────────────────────────────────
        BigDecimal salesToday = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(total),0) FROM sales WHERE tenant_id=? AND status='COMPLETED' " +
                "AND completed_at::date = ?", BigDecimal.class, tenant, today));
        long salesCountToday = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales WHERE tenant_id=? AND status='COMPLETED' " +
                "AND completed_at::date = ?", Long.class, tenant, today));
        BigDecimal salesYesterday = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(total),0) FROM sales WHERE tenant_id=? AND status='COMPLETED' " +
                "AND completed_at::date = ?", BigDecimal.class, tenant, yesterday));
        BigDecimal salesMonth = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(total),0) FROM sales WHERE tenant_id=? AND status='COMPLETED' " +
                "AND completed_at::date >= ?", BigDecimal.class, tenant, monthStart));
        long salesCountMonth = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sales WHERE tenant_id=? AND status='COMPLETED' " +
                "AND completed_at::date >= ?", Long.class, tenant, monthStart));
        BigDecimal avgTicketToday = avg(salesToday, salesCountToday);
        BigDecimal avgTicketMonth = avg(salesMonth, salesCountMonth);

        // ── Expenses ─────────────────────────────────────────────────────────
        BigDecimal expensesMonth = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM expenses WHERE tenant_id=? AND expense_date >= ?",
                BigDecimal.class, tenant, monthStart));
        long pendingApprovals = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE tenant_id=? AND approval_status='PENDING'",
                Long.class, tenant));

        // ── Invoices ─────────────────────────────────────────────────────────
        long unpaidInvoicesCount = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE tenant_id=? AND balance>0 AND status NOT IN ('CANCELLED','PAID')",
                Long.class, tenant));
        BigDecimal unpaidInvoicesAmount = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(balance),0) FROM invoices WHERE tenant_id=? AND balance>0 " +
                "AND status NOT IN ('CANCELLED','PAID')",
                BigDecimal.class, tenant));
        long overdueInvoicesCount = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE tenant_id=? AND balance>0 AND due_date < ? " +
                "AND status NOT IN ('CANCELLED','PAID')",
                Long.class, tenant, today));
        BigDecimal overdueInvoicesAmount = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(balance),0) FROM invoices WHERE tenant_id=? AND balance>0 AND due_date < ? " +
                "AND status NOT IN ('CANCELLED','PAID')",
                BigDecimal.class, tenant, today));
        BigDecimal cashReceivedToday = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM payments WHERE tenant_id=? AND status='CONFIRMED' " +
                "AND payment_date = ? AND type IN ('CUSTOMER_PAYMENT','CUSTOMER_DEPOSIT')",
                BigDecimal.class, tenant, today));

        // ── Stock & ops ──────────────────────────────────────────────────────
        BigDecimal stockValueTotal = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(stock_value),0) FROM v_report_stock WHERE tenant_id=?",
                BigDecimal.class, tenant));
        long lowStockCount = nz(jdbc.queryForObject(
                "SELECT COUNT(DISTINCT product_id) FROM v_report_stock WHERE tenant_id=? AND is_low_stock=true",
                Long.class, tenant));
        long pendingDeliveries = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM deliveries WHERE tenant_id=? AND status IN ('PENDING','PREPARING','READY')",
                Long.class, tenant));
        long openSessions = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM cash_sessions WHERE tenant_id=? AND status='OPEN'",
                Long.class, tenant));
        long expiring30 = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_lots WHERE tenant_id=? AND status='ACTIVE' " +
                "AND expiration_date BETWEEN ? AND ?",
                Long.class, tenant, today, today.plusDays(30)));
        long expired = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_lots WHERE tenant_id=? " +
                "AND (status='EXPIRED' OR (status='ACTIVE' AND expiration_date < ?))",
                Long.class, tenant, today));

        // ── Users & customers ────────────────────────────────────────────────
        long activeUsers = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE tenant_id=? AND is_active=true",
                Long.class, tenant));
        long activeCustomers = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM parties WHERE tenant_id=? AND is_customer=true AND active=true",
                Long.class, tenant));
        long overCreditLimit = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM parties c JOIN ar_balances ab ON ab.party_id=c.id " +
                "WHERE c.tenant_id=? AND c.is_customer=true AND c.active=true " +
                "AND c.customer_credit_limit > 0 AND ab.balance > c.customer_credit_limit",
                Long.class, tenant));
        BigDecimal totalCustomerBalance = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(balance),0) FROM ar_balances WHERE tenant_id=?",
                BigDecimal.class, tenant));

        // ── Aging buckets (sums across all unpaid invoices) ──────────────────
        BigDecimal[] aging = jdbc.queryForObject(
                "SELECT " +
                "  COALESCE(SUM(CASE WHEN due_date IS NULL OR due_date >= ? THEN balance ELSE 0 END), 0), " +
                "  COALESCE(SUM(CASE WHEN due_date BETWEEN ? AND ? THEN balance ELSE 0 END), 0), " +
                "  COALESCE(SUM(CASE WHEN due_date BETWEEN ? AND ? THEN balance ELSE 0 END), 0), " +
                "  COALESCE(SUM(CASE WHEN due_date BETWEEN ? AND ? THEN balance ELSE 0 END), 0), " +
                "  COALESCE(SUM(CASE WHEN due_date < ? THEN balance ELSE 0 END), 0) " +
                "FROM invoices WHERE tenant_id=? AND balance>0 AND status NOT IN ('CANCELLED','PAID')",
                (rs, i) -> new BigDecimal[]{
                        rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5)
                },
                today,
                today.minusDays(30), today.minusDays(1),
                today.minusDays(60), today.minusDays(31),
                today.minusDays(90), today.minusDays(61),
                today.minusDays(90),
                tenant);
        BigDecimal agingCurrent = aging != null ? nz(aging[0]) : BigDecimal.ZERO;
        BigDecimal aging1to30 = aging != null ? nz(aging[1]) : BigDecimal.ZERO;
        BigDecimal aging31to60 = aging != null ? nz(aging[2]) : BigDecimal.ZERO;
        BigDecimal aging61to90 = aging != null ? nz(aging[3]) : BigDecimal.ZERO;
        BigDecimal aging90plus = aging != null ? nz(aging[4]) : BigDecimal.ZERO;

        // ── Invoices backlog ─────────────────────────────────────────────────
        long invoicesDraftCount = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoices WHERE tenant_id=? AND status='DRAFT'",
                Long.class, tenant));
        // Non-cancelled non-draft invoices not yet fully delivered (returned ones are out of scope).
        long invoicesNotFullyDeliveredCount = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM invoices " +
                "WHERE tenant_id=? AND status NOT IN ('DRAFT','CANCELLED') " +
                "  AND delivery_status NOT IN ('DELIVERED','RETURNED')",
                Long.class, tenant));

        // Credit notes issued (this month) — count and aggregated total. Replaces
        // the former "refunded invoices" KPI: an avoir no longer flips the invoice
        // status, it stands as its own document.
        long creditNotesCountMonth = nz(jdbc.queryForObject(
                "SELECT COUNT(*) FROM credit_notes WHERE tenant_id=? AND status <> 'DRAFT' AND issue_date >= ?",
                Long.class, tenant, monthStart));
        BigDecimal creditNotesAmountMonth = nz(jdbc.queryForObject(
                "SELECT COALESCE(SUM(total),0) FROM credit_notes WHERE tenant_id=? AND status <> 'DRAFT' AND issue_date >= ?",
                BigDecimal.class, tenant, monthStart));

        // ── Top 5 products (by revenue) this month ───────────────────────────
        List<ReportingDto.TopProductRow> topProducts = jdbc.query(
                "SELECT sl.product_id, COALESCE(p.name, sl.snapshot_name) AS product_name, " +
                "       COALESCE(p.sku, sl.snapshot_sku) AS sku, " +
                "       ?::date AS sale_month, " +
                "       SUM(sl.quantity) AS qty_sold, SUM(sl.total) AS revenue " +
                "FROM sale_lines sl " +
                "JOIN sales s ON s.id = sl.sale_id AND s.tenant_id = sl.tenant_id " +
                "LEFT JOIN products p ON p.id = sl.product_id AND p.tenant_id = sl.tenant_id " +
                "WHERE sl.tenant_id = ? AND s.status='COMPLETED' AND s.completed_at::date >= ? " +
                "GROUP BY sl.product_id, COALESCE(p.name, sl.snapshot_name), COALESCE(p.sku, sl.snapshot_sku) " +
                "ORDER BY revenue DESC NULLS LAST LIMIT 5",
                (rs, i) -> new ReportingDto.TopProductRow(
                        rs.getObject("product_id", UUID.class),
                        rs.getString("product_name"),
                        rs.getString("sku"),
                        rs.getObject("sale_month", LocalDate.class),
                        rs.getBigDecimal("qty_sold"),
                        rs.getBigDecimal("revenue")),
                monthStart, tenant, monthStart);

        // ── Sales last 7 days (incl. today) ──────────────────────────────────
        List<ReportingDto.DailyAmount> sales7Days = jdbc.query(
                "WITH days AS ( " +
                "  SELECT generate_series(?::date, ?::date, '1 day')::date AS day " +
                ") " +
                "SELECT d.day, COALESCE(SUM(s.total), 0) AS amount " +
                "FROM days d " +
                "LEFT JOIN sales s ON s.tenant_id = ? AND s.status='COMPLETED' AND s.completed_at::date = d.day " +
                "GROUP BY d.day ORDER BY d.day",
                (rs, i) -> new ReportingDto.DailyAmount(
                        rs.getObject("day", LocalDate.class),
                        rs.getBigDecimal("amount")),
                sevenDaysAgo, today, tenant);

        // ── Payment method breakdown for today ───────────────────────────────
        List<ReportingDto.PaymentMethodAmount> paymentMethodsToday = jdbc.query(
                "SELECT method, COUNT(*) AS cnt, COALESCE(SUM(amount),0) AS amt " +
                "FROM payments WHERE tenant_id=? AND status='CONFIRMED' AND payment_date = ? " +
                "GROUP BY method ORDER BY amt DESC",
                (rs, i) -> new ReportingDto.PaymentMethodAmount(
                        rs.getString("method"),
                        rs.getLong("cnt"),
                        rs.getBigDecimal("amt")),
                tenant, today);

        return new ReportingDto.DashboardKpis(
                salesToday, salesCountToday, salesYesterday, avgTicketToday,
                salesMonth, salesCountMonth, avgTicketMonth,
                expensesMonth, pendingApprovals,
                unpaidInvoicesCount, unpaidInvoicesAmount,
                overdueInvoicesCount, overdueInvoicesAmount,
                cashReceivedToday,
                stockValueTotal, lowStockCount, pendingDeliveries, openSessions,
                expiring30, expired,
                activeUsers, activeCustomers, overCreditLimit, totalCustomerBalance,
                agingCurrent, aging1to30, aging31to60, aging61to90, aging90plus,
                invoicesDraftCount, invoicesNotFullyDeliveredCount,
                creditNotesCountMonth, creditNotesAmountMonth,
                topProducts, sales7Days, paymentMethodsToday);
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static long nz(Long v) { return v == null ? 0L : v; }
    private static BigDecimal avg(BigDecimal total, long count) {
        if (count <= 0) return BigDecimal.ZERO;
        return total.divide(BigDecimal.valueOf(count), 2, java.math.RoundingMode.HALF_UP);
    }
}
