package com.minierp.phase1b;

import com.minierp.MiniErpApplication;
import com.minierp.delivery.api.DeliveryDto;
import com.minierp.delivery.internal.DeliveryService;
import com.minierp.sales.api.SalesDto;
import com.minierp.sales.internal.SalesService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avoir total + auto BR: an avoir cancels the whole invoice in one shot.
 * Stock-return behavior:
 *  - Fully shipped → BR for everything, stock back.
 *  - Never shipped → no BR, status flips to DELIVERED (nothing left to ship).
 *  - Partially shipped → BR for the delivered slice only; the never-shipped
 *    slice cancels without a stock movement.
 */
@SpringBootTest(classes = MiniErpApplication.class)
@ActiveProfiles("test")
@DisplayName("Credit note auto return BL — stock-in via BR not via NC")
class CreditNoteReturnDeliveryIT {

    @Autowired SalesService salesService;
    @Autowired DeliveryService deliveryService;
    @Autowired JdbcTemplate jdbc;

    @PersistenceContext EntityManager em;

    UUID tenantId;
    UUID customerId;
    UUID productId;
    UUID uomId;
    UUID warehouseId;

    @BeforeEach
    void setup() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'CN Return Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "cnret-" + tenantId);

        TenantContext.set(tenantId);
        em.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class, tenantId.toString());

        UUID uomCatId = UUID.randomUUID();
        uomId = UUID.randomUUID();
        productId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();

        jdbc.update("INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version) VALUES (?,?,?,?,now(),now(),0)",
                uomCatId, tenantId, "COUNT-CNRET", "Count");
        jdbc.update("INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base, decimal_places, created_at, updated_at, version) VALUES (?,?,?,?,?,1,true,0,now(),now(),0)",
                uomId, tenantId, uomCatId, "PCE-CNRET", "Piece");
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, tracks_serial, is_sellable, is_purchasable,
                                      is_active, created_at, updated_at, version)
                VALUES (?,?,?,?,?,0.00,false,false,true,true,true,now(),now(),0)
                """, productId, tenantId, "SKU-CNRET", "Widget", uomId);
        jdbc.update("INSERT INTO parties (id, tenant_id, code, name, is_customer, is_supplier, active, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,false,true,now(),now(),0)",
                customerId, tenantId, "C-CNRET-" + customerId, "CN Return Customer");

        jdbc.update("INSERT INTO warehouses (id, tenant_id, code, name, is_default, is_active, " +
                "type, created_at, updated_at, version) " +
                "VALUES (?,?,?,?,true,true,'MAIN',now(),now(),0)",
                warehouseId, tenantId, "WH-CNRET", "CN Return Warehouse");
        jdbc.update("INSERT INTO stocks (id, tenant_id, warehouse_id, product_id, " +
                "qty_on_hand, qty_reserved, average_cost, created_at, updated_at, version) " +
                "VALUES (uuid_generate_v4(),?,?,?,?,0,?,now(),now(),0)",
                tenantId, warehouseId, productId,
                new BigDecimal("100"), new BigDecimal("100"));
    }

    @Test
    void fullyShippedThenTotalAvoirCreatesBrAndReturnsStock() {
        SalesDto.InvoiceDto inv = createInvoice(new BigDecimal("3"), new BigDecimal("100"));
        shipInvoiceFully(inv.id(), new BigDecimal("3"));
        BigDecimal stockBefore = currentStock();

        salesService.createCreditNote(inv.id(),
                new SalesDto.CreateCreditNoteRequest("Return all"));

        Map<String, Object> br = findReturnDeliveryFor(inv.id());
        assertThat(br).as("a BR must be auto-created").isNotNull();
        assertThat((String) br.get("number")).startsWith("BR-");
        assertThat((String) br.get("status")).isEqualTo("DELIVERED");
        assertThat((String) br.get("type")).isEqualTo("RETURN");

        BigDecimal brQty = sumReturnQtyForDelivery((UUID) br.get("id"));
        assertThat(brQty).isEqualByComparingTo("3");
        assertThat(currentStock()).isEqualByComparingTo(stockBefore.add(new BigDecimal("3")));
    }

    @Test
    void totalAvoirOnNeverShippedDoesNotCreateBr() {
        SalesDto.InvoiceDto inv = createInvoice(new BigDecimal("3"), new BigDecimal("100"));
        assertThat(salesService.getInvoice(inv.id()).deliveryStatus()).isEqualTo("NONE");

        salesService.createCreditNote(inv.id(),
                new SalesDto.CreateCreditNoteRequest("Wrong invoice"));

        assertThat(findReturnDeliveryFor(inv.id())).as("nothing shipped → no BR").isNull();
        assertThat(salesService.getInvoice(inv.id()).deliveryStatus())
                .as("fully credited invoice has no outstanding delivery")
                .isEqualTo("DELIVERED");
    }

    @Test
    void totalAvoirOnPartiallyShippedCreatesBrForShippedSliceOnly() {
        SalesDto.InvoiceDto inv = createInvoice(new BigDecimal("3"), new BigDecimal("100"));
        shipInvoiceFully(inv.id(), new BigDecimal("1"));
        assertThat(salesService.getInvoice(inv.id()).deliveryStatus())
                .isEqualTo("PARTIALLY_DELIVERED");

        salesService.createCreditNote(inv.id(),
                new SalesDto.CreateCreditNoteRequest("Take back"));

        Map<String, Object> br = findReturnDeliveryFor(inv.id());
        assertThat(br).as("BR expected for the shipped portion").isNotNull();
        assertThat(sumReturnQtyForDelivery((UUID) br.get("id")))
                .isEqualByComparingTo("1");

        assertThat(salesService.getInvoice(inv.id()).deliveryStatus())
                .as("once the unshipped portion is cancelled and the shipped portion returned, everything is settled")
                .isEqualTo("DELIVERED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private SalesDto.InvoiceDto createInvoice(BigDecimal qty, BigDecimal unitPrice) {
        return salesService.createInvoice(new SalesDto.CreateInvoiceRequest(
                customerId, null, LocalDate.now(), LocalDate.now().plusDays(30),
                null, "MRU", null,
                List.of(new SalesDto.LineRequest(productId, uomId, qty, unitPrice, BigDecimal.ZERO))));
    }

    private void shipInvoiceFully(UUID invoiceId, BigDecimal shipQty) {
        DeliveryDto.CreateDeliveryRequest create = new DeliveryDto.CreateDeliveryRequest(
                customerId, invoiceId, warehouseId, LocalDate.now(), null, null, null,
                List.of(new DeliveryDto.LineRequest(productId, uomId, shipQty, "Widget", "SKU-CNRET")));
        DeliveryDto.DeliveryResponse d = deliveryService.create(create, null);
        deliveryService.startDelivery(d.id(), null);
        UUID lineId = d.lines().get(0).id();
        deliveryService.recordDelivery(d.id(),
                new DeliveryDto.RecordDeliveryRequest(
                        List.of(new DeliveryDto.LineDelivered(lineId, shipQty)),
                        "Receiver", null),
                null);
    }

    private Map<String, Object> findReturnDeliveryFor(UUID invoiceId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, number, status, type
                FROM deliveries
                WHERE invoice_id = ? AND type = 'RETURN'
                """, invoiceId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private BigDecimal sumReturnQtyForDelivery(UUID deliveryId) {
        Number n = jdbc.queryForObject("""
                SELECT COALESCE(SUM(quantity_delivered), 0)
                FROM delivery_lines
                WHERE delivery_id = ?
                """, Number.class, deliveryId);
        return n == null ? BigDecimal.ZERO : new BigDecimal(n.toString());
    }

    private BigDecimal currentStock() {
        return jdbc.queryForObject("SELECT qty_on_hand FROM stocks WHERE warehouse_id = ? AND product_id = ?",
                BigDecimal.class, warehouseId, productId);
    }
}
