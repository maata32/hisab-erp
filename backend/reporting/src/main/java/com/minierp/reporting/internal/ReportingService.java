package com.minierp.reporting.internal;

import com.minierp.reporting.api.ReportingDto;
import com.minierp.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                  AND (? IS NULL OR sale_day >= ?)
                  AND (? IS NULL OR sale_day <= ?)
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
                  AND (? IS NULL OR sale_day >= ?)
                  AND (? IS NULL OR sale_day <= ?)
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
                  AND (? IS NULL OR warehouse_id = ?)
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
                  AND (? IS NULL OR days_remaining <= ?)
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
                  AND (? IS NULL OR payment_day >= ?)
                  AND (? IS NULL OR payment_day <= ?)
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
                  AND (? IS NULL OR e.warehouse_id = ?)
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
                SELECT c.id   AS customer_id,
                       c.code AS customer_code,
                       c.name AS customer_name,
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
                FROM customers c
                LEFT JOIN invoices i
                       ON i.customer_id = c.id
                      AND i.tenant_id = c.tenant_id
                      AND i.balance > 0
                      AND i.status NOT IN ('CANCELLED','PAID')
                WHERE c.tenant_id = ?
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
                  AND (? IS NULL OR sale_month = ?)
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
}
