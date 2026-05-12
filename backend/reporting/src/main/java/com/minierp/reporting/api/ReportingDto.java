package com.minierp.reporting.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class ReportingDto {

    private ReportingDto() {}

    /** v_report_direction — revenue/gross profit by day, month, year */
    public record DirectionRow(
            LocalDate saleDay,
            LocalDate saleMonth,
            int saleYear,
            long saleCount,
            BigDecimal revenue,
            BigDecimal grossProfit) {}

    /** v_report_caisse — sales per cashier/register/payment method */
    public record CaisseRow(
            UUID cashierUserId,
            UUID registerId,
            LocalDate saleDay,
            String paymentMethod,
            long saleCount,
            BigDecimal totalRevenue,
            BigDecimal avgTicket) {}

    /** v_report_stock — stock value + low stock flag per product/warehouse */
    public record StockRow(
            UUID warehouseId,
            UUID productId,
            String productName,
            String sku,
            BigDecimal qtyOnHand,
            BigDecimal qtyReserved,
            BigDecimal qtyAvailable,
            BigDecimal averageCost,
            BigDecimal stockValue,
            boolean isLowStock) {}

    /** v_report_expiry — lots nearing expiration with risk level */
    public record ExpiryRow(
            UUID lotId,
            String lotNumber,
            UUID productId,
            String productName,
            UUID warehouseId,
            LocalDate expirationDate,
            int daysRemaining,
            BigDecimal quantityRemaining,
            String status,
            String riskLevel) {}

    /** v_report_payments — payment aggregates by day/month */
    public record PaymentRow(
            LocalDate paymentDay,
            LocalDate paymentMonth,
            String type,
            String method,
            long paymentCount,
            BigDecimal totalAmount) {}

    /** v_report_deliveries — delivery counts by status */
    public record DeliveryRow(
            String status,
            long deliveryCount,
            long onTimeCount,
            long lateCount) {}

    /** v_report_top_products — top products by revenue/qty per month */
    public record TopProductRow(
            UUID productId,
            String productName,
            String sku,
            LocalDate saleMonth,
            BigDecimal qtySold,
            BigDecimal revenue) {}

    /** CDC §15.4 GET /reports/expiry-risk — stock value at risk by risk bucket. */
    public record ExpiryRiskRow(
            String riskLevel,
            long lotCount,
            BigDecimal quantityAtRisk,
            BigDecimal valueAtRisk) {}

    /** CDC §15.4 GET /reports/aging — customer balance bucketed by overdue age. */
    public record AgingRow(
            UUID customerId,
            String customerCode,
            String customerName,
            BigDecimal current,
            BigDecimal d1to30,
            BigDecimal d31to60,
            BigDecimal d61to90,
            BigDecimal d90plus,
            BigDecimal totalOutstanding) {}
}
