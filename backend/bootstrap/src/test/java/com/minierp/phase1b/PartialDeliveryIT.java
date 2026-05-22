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
 * Mandatory spec test: 5 sequential partial deliveries for a 10-unit line.
 * Verifies PARTIAL status on rounds 1-4, DELIVERED on round 5.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Partial delivery — 5 rounds of 2 units each, final status DELIVERED")
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

        jdbc.update("INSERT INTO parties (id, tenant_id, customer_code, name, is_customer, is_supplier, active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,false,true,now(),now(),0)",
                customerId, tenantId, "C-DELTEST", "Delivery Test Customer");
    }

    @Test
    void fivePartialDeliveriesReachDelivered() {
        // Create delivery with 1 line of 10 units
        DeliveryDto.CreateDeliveryRequest create = new DeliveryDto.CreateDeliveryRequest(
                customerId, null, LocalDate.now(), null, null, null,
                List.of(new DeliveryDto.LineRequest(productId, uomId, new BigDecimal("10"), "Widget", "SKU-001"))
        );
        DeliveryDto.DeliveryResponse delivery = deliveryService.create(create, null);

        // Start delivery
        deliveryService.startDelivery(delivery.id(), null);

        UUID lineId = delivery.lines().get(0).id();

        // Rounds 1-4: deliver 2 units each → should be PARTIAL
        for (int round = 1; round <= 4; round++) {
            DeliveryDto.DeliveryResponse result = deliveryService.recordDelivery(
                    delivery.id(),
                    new DeliveryDto.RecordDeliveryRequest(
                            List.of(new DeliveryDto.LineDelivered(lineId, new BigDecimal("2"))),
                            null, null),
                    null);
            assertThat(result.status()).as("Round %d status", round).isEqualTo("PARTIAL");
        }

        // Round 5: deliver final 2 units → should be DELIVERED
        DeliveryDto.DeliveryResponse final_ = deliveryService.recordDelivery(
                delivery.id(),
                new DeliveryDto.RecordDeliveryRequest(
                        List.of(new DeliveryDto.LineDelivered(lineId, new BigDecimal("2"))),
                        "Test Receiver", null),
                null);

        assertThat(final_.status()).isEqualTo("DELIVERED");
        assertThat(final_.lines().get(0).quantityDelivered()).isEqualByComparingTo("10");
        assertThat(final_.lines().get(0).status()).isEqualTo("COMPLETED");
    }
}
