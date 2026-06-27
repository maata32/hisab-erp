package com.hisaberp.web;

import com.hisaberp.HisabErpApplication;
import com.hisaberp.identity.security.JwtService;
import com.hisaberp.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HisabErpApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("InventoryController — HTTP layer")
class InventoryControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;
    UUID warehouseId;
    UUID productId;
    String token;

    @BeforeEach
    void seed() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "org-" + tenantId);

        UUID uomCatId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, 'COUNT', 'Count', now(), now(), 0)
                """, uomCatId, tenantId);

        UUID uomId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name,
                                  ratio_to_base, is_base, decimal_places,
                                  created_at, updated_at, version)
                VALUES (?, ?, ?, 'PCE', 'Piece', 1, true, 0, now(), now(), 0)
                """, uomId, tenantId, uomCatId);

        productId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id,
                                      default_tax_rate, tracks_lots, tracks_serial,
                                      is_sellable, is_purchasable, is_active,
                                      created_at, updated_at, version)
                VALUES (?, ?, ?, 'Inv Product', ?, 0.00, false, false, true, true,
                        true, now(), now(), 0)
                """, productId, tenantId, "INV-" + productId, uomId);

        // Variant = SKU model: every product owns a default variant. Tests reuse the
        // product id as its default variant id so seeds/payloads keep one identifier.
        jdbc.update("""
                INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default,
                                              is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, true, now(), now(), 0)
                """, productId, tenantId, productId, "INV-V-" + productId);

        warehouseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouses (id, tenant_id, code, name, is_default, is_active,
                                        created_at, updated_at, version)
                VALUES (?, ?, 'MAIN', 'Main Warehouse', true, true, now(), now(), 0)
                """, warehouseId, tenantId);

        token = jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), tenantId, "test@test.local", "fr",
                Set.of(), Set.of("warehouse:manage", "stock:read", "stock:adjust", "inventory:count")));
    }

    @Test
    @DisplayName("POST /inventory/warehouses returns 201 with new warehouse")
    void createWarehouse_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/warehouses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WHTEST","name":"Test Warehouse"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.code").value("WHTEST"));
    }

    @Test
    @DisplayName("GET /inventory/stocks/{wId}/{pId} returns 200 with zero-stock dto")
    void getStock_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/stocks/{wId}/{pId}", warehouseId, productId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouseId").value(warehouseId.toString()))
                .andExpect(jsonPath("$.productId").value(productId.toString()));
    }

    @Test
    @DisplayName("POST /inventory/counts returns 201 and persists line with count_id")
    void createInventoryCount_persistsLines() throws Exception {
        jdbc.update("""
                INSERT INTO stocks (id, tenant_id, warehouse_id, variant_id, product_id,
                                    qty_on_hand, qty_reserved, average_cost,
                                    created_at, updated_at, version)
                VALUES (uuid_generate_v4(), ?, ?, ?, ?, 10, 0, 5, now(), now(), 0)
                """, tenantId, warehouseId, productId, productId);

        mockMvc.perform(post("/api/v1/inventory/counts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"warehouseId\":\"" + warehouseId + "\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].variantId").value(productId.toString()))
                .andExpect(jsonPath("$.lines[0].theoreticalQty").value(10));

        Integer linesWithNullParent = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_count_lines WHERE count_id IS NULL AND tenant_id = ?",
                Integer.class, tenantId);
        assert linesWithNullParent != null && linesWithNullParent == 0
                : "Expected no orphan inventory_count_lines, found " + linesWithNullParent;
    }
}
