package com.hisaberp.phase1b;

import com.hisaberp.HisabErpApplication;
import com.hisaberp.delivery.api.DeliveryDto;
import com.hisaberp.delivery.internal.DeliveryService;
import com.hisaberp.shared.error.BusinessException;
import com.hisaberp.shared.tenant.TenantContext;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Business rule: recording a delivery is all-or-nothing. The full remaining
 * quantity ships on every line in one call; the delivery transitions straight
 * to DELIVERED. There is no PARTIAL delivery status by design.
 */
@SpringBootTest(classes = HisabErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Delivery recording is all-or-nothing — straight to DELIVERED")
class PartialDeliveryIT {

    @Autowired
    DeliveryService deliveryService;

    @Autowired
    JdbcTemplate jdbc;

    @PersistenceContext
    EntityManager em;

    UUID tenantId;
    UUID customerId;
    UUID productId;
    UUID uomId;
    UUID invoiceId;
    UUID warehouseId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Delivery Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "deltest-" + tenantId);

        TenantContext.set(tenantId);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());

        customerId = UUID.randomUUID();
        uomId = UUID.randomUUID();
        productId = UUID.randomUUID();

        jdbc.update("INSERT INTO parties (id, tenant_id, code, name, is_customer, is_supplier, active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,false,true,now(),now(),0)",
                customerId, tenantId, "C-DELTEST-" + customerId, "Delivery Test Customer");

        // Variant = SKU model: seed a default variant reusing the product id so the
        // delivery decrement (StockOperations resolves the product via the variant) works.
        jdbc.update("INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default, is_active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,true,now(),now(),0)",
                productId, tenantId, productId, "SKU-DELTEST-" + productId);

        // Business rule: delivery requires a non-cancelled invoice. Seed one directly.
        invoiceId = UUID.randomUUID();
        jdbc.update("INSERT INTO invoices (id, tenant_id, number, party_id, issue_date, status, currency, " +
                "subtotal, discount_amount, tax_amount, total, paid_amount, balance, " +
                "created_at, updated_at, version) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,now(),now(),0)",
                invoiceId, tenantId, "INV-DELTEST", customerId, LocalDate.now(), "ISSUED", "MRU",
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"));

        // Warehouse + stock seed (recordDelivery now decrements stock).
        warehouseId = UUID.randomUUID();
        jdbc.update("INSERT INTO warehouses (id, tenant_id, code, name, is_default, is_active, " +
                "type, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,true,'MAIN',now(),now(),0)",
                warehouseId, tenantId, "WH-DELTEST", "Delivery Test Warehouse");
        jdbc.update("INSERT INTO stocks (id, tenant_id, warehouse_id, variant_id, product_id, " +
                "qty_on_hand, qty_reserved, average_cost, created_at, updated_at, version) " +
                "VALUES (uuid_generate_v4(),?,?,?,?,?,0,?,now(),now(),0)",
                tenantId, warehouseId, productId, productId, new BigDecimal("100"), new BigDecimal("10"));
    }

    @Test
    void recordingShipsFullQuantityAndMarksDelivered() {
        DeliveryDto.CreateDeliveryRequest create = new DeliveryDto.CreateDeliveryRequest(
                customerId, invoiceId, warehouseId, LocalDate.now(), null, null, null,
                List.of(new DeliveryDto.LineRequest(productId, productId, uomId, new BigDecimal("10"), "Widget", "SKU-001"))
        );
        DeliveryDto.DeliveryResponse delivery = deliveryService.create(create, null);
        deliveryService.startDelivery(delivery.id(), null);

        UUID lineId = delivery.lines().get(0).id();

        // Even when the caller passes a partial quantity, the server ships the full
        // remaining qty on every line and the delivery transitions directly to DELIVERED.
        DeliveryDto.DeliveryResponse result = deliveryService.recordDelivery(
                delivery.id(),
                new DeliveryDto.RecordDeliveryRequest(
                        List.of(new DeliveryDto.LineDelivered(lineId, new BigDecimal("2"))),
                        "Test Receiver", null),
                null);

        assertThat(result.status()).isEqualTo("DELIVERED");
        assertThat(result.lines().get(0).quantityDelivered()).isEqualByComparingTo("10");
        assertThat(result.lines().get(0).status()).isEqualTo("COMPLETED");

        // Re-recording a DELIVERED BL is rejected.
        assertThatThrownBy(() -> deliveryService.recordDelivery(
                delivery.id(),
                new DeliveryDto.RecordDeliveryRequest(
                        List.of(new DeliveryDto.LineDelivered(lineId, new BigDecimal("1"))),
                        null, null),
                null))
                .isInstanceOf(BusinessException.class);
    }
}
