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
