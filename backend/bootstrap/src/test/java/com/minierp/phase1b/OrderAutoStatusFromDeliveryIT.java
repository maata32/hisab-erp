package com.minierp.phase1b;

import com.minierp.MiniErpApplication;
import com.minierp.delivery.api.DeliveryDto;
import com.minierp.delivery.internal.DeliveryService;
import com.minierp.shared.tenant.TenantContext;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that recording delivered quantities auto-derives the linked order's
 * status: CONFIRMED → PARTIALLY_DELIVERED on first partial shipment, then
 * DELIVERED once cumulative delivered quantity meets the ordered quantity.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Order status is auto-derived from delivery shipments")
class OrderAutoStatusFromDeliveryIT {

    @Autowired DeliveryService deliveryService;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;

    UUID tenantId;
    UUID customerId;
    UUID productId;
    UUID uomId;
    UUID invoiceId;
    UUID warehouseId;
    UUID orderId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Order Auto Status Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "ordauto-" + tenantId);

        TenantContext.set(tenantId);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());

        customerId = UUID.randomUUID();
        uomId = UUID.randomUUID();
        productId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();

        jdbc.update("INSERT INTO parties (id, tenant_id, code, name, is_customer, is_supplier, active, " +
                "created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,false,true,now(),now(),0)",
                customerId, tenantId, "C-ORDAUTO-" + customerId, "Order Auto Customer");

        // INVOICED order with a single line of 10 units. Matches the realistic flow:
        // DRAFT → CONFIRMED → INVOICED (here) → PARTIALLY_DELIVERED → DELIVERED.
        jdbc.update("INSERT INTO orders (id, tenant_id, number, party_id, order_date, status, " +
                "delivery_required, currency, subtotal, discount_amount, tax_amount, total, " +
                "created_at, updated_at, version) " +
                "VALUES (?,?,?,?,?,?,true,'MRU',?,0,0,?,now(),now(),0)",
                orderId, tenantId, "SO-ORDAUTO", customerId, LocalDate.now(), "INVOICED",
                new BigDecimal("100"), new BigDecimal("100"));
        jdbc.update("INSERT INTO order_lines (id, tenant_id, order_id, line_number, product_id, uom_id, " +
                "quantity, unit_price, discount_percent, tax_rate, line_total, " +
                "created_at, updated_at, version) " +
                "VALUES (uuid_generate_v4(),?,?,1,?,?,?,?,0,0,?,now(),now(),0)",
                tenantId, orderId, productId, uomId,
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100"));

        jdbc.update("INSERT INTO invoices (id, tenant_id, number, party_id, order_id, issue_date, status, currency, " +
                "subtotal, discount_amount, tax_amount, total, paid_amount, balance, " +
                "created_at, updated_at, version) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,now(),now(),0)",
                invoiceId, tenantId, "INV-ORDAUTO", customerId, orderId, LocalDate.now(), "ISSUED", "MRU",
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"));

        jdbc.update("INSERT INTO warehouses (id, tenant_id, code, name, is_default, is_active, " +
                "type, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,true,'MAIN',now(),now(),0)",
                warehouseId, tenantId, "WH-ORDAUTO", "Order Auto Warehouse");
        jdbc.update("INSERT INTO stocks (id, tenant_id, warehouse_id, product_id, " +
                "qty_on_hand, qty_reserved, average_cost, created_at, updated_at, version) " +
                "VALUES (uuid_generate_v4(),?,?,?,?,0,?,now(),now(),0)",
                tenantId, warehouseId, productId, new BigDecimal("100"), new BigDecimal("10"));
    }

    @Test
    void splitDeliveryAcrossTwoBLsDrivesOrderStatus() {
        // Initial state guard.
        assertThat(currentOrderStatus()).isEqualTo("INVOICED");

        // Each BL is all-or-nothing — to model an order-level partial delivery the
        // user creates two BLs against the same order, each covering a subset of qty.

        // BL1 covers 4 of the 10 ordered units.
        DeliveryDto.DeliveryResponse bl1 = deliveryService.create(
                new DeliveryDto.CreateDeliveryRequest(
                        customerId, orderId, invoiceId, warehouseId, LocalDate.now(), null, null, null,
                        List.of(new DeliveryDto.LineRequest(productId, uomId, new BigDecimal("4"), "Widget", "SKU-ORDAUTO"))
                ), null);
        deliveryService.startDelivery(bl1.id(), null);
        deliveryService.recordDelivery(
                bl1.id(),
                new DeliveryDto.RecordDeliveryRequest(List.of(), "Receiver", null),
                null);
        assertThat(currentOrderStatus())
                .as("After the first BL ships, the order should be PARTIALLY_DELIVERED")
                .isEqualTo("PARTIALLY_DELIVERED");

        // BL2 covers the remaining 6 units.
        DeliveryDto.DeliveryResponse bl2 = deliveryService.create(
                new DeliveryDto.CreateDeliveryRequest(
                        customerId, orderId, invoiceId, warehouseId, LocalDate.now(), null, null, null,
                        List.of(new DeliveryDto.LineRequest(productId, uomId, new BigDecimal("6"), "Widget", "SKU-ORDAUTO"))
                ), null);
        deliveryService.startDelivery(bl2.id(), null);
        deliveryService.recordDelivery(
                bl2.id(),
                new DeliveryDto.RecordDeliveryRequest(List.of(), "Receiver", null),
                null);
        assertThat(currentOrderStatus())
                .as("After the second BL ships and covers the order, status should be DELIVERED")
                .isEqualTo("DELIVERED");
    }

    private String currentOrderStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }
}
