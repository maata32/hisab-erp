package com.minierp.web;

import com.jayway.jsonpath.JsonPath;
import com.minierp.MiniErpApplication;
import com.minierp.identity.security.JwtService;
import com.minierp.shared.security.CurrentUser;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MiniErpApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("PosController — HTTP layer")
class PosControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;
    UUID registerId;
    UUID productId;
    UUID uomId;
    String token;

    @BeforeEach
    void seed() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'POS Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "pos-org-" + tenantId);

        UUID uomCatId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, 'COUNT', 'Count', now(), now(), 0)
                """, uomCatId, tenantId);

        uomId = UUID.randomUUID();
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
                VALUES (?, ?, ?, 'POS Product', ?, 0.00, false, false, true, true,
                        true, now(), now(), 0)
                """, productId, tenantId, "POS-" + productId, uomId);

        // Variant = SKU: POS resolves price + destocks by variant; seed the default
        // variant reusing the product id.
        jdbc.update("""
                INSERT INTO product_variants (id, tenant_id, product_id, sku, is_default,
                                              is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, true, now(), now(), 0)
                """, productId, tenantId, productId, "POS-" + productId);

        UUID tierId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO price_tiers (id, tenant_id, code, name, is_default, is_active,
                                         created_at, updated_at, version)
                VALUES (?, ?, 'RETAIL', 'Retail', true, true, now(), now(), 0)
                """, tierId, tenantId);

        jdbc.update("""
                INSERT INTO product_prices (id, tenant_id, variant_id, product_id, uom_id, price_tier_id,
                                            amount, currency, tax_inclusive, valid_from,
                                            created_at, updated_at, version)
                VALUES (uuid_generate_v4(), ?, ?, ?, ?, ?, 100.00, 'MRU', false, '2000-01-01',
                        now(), now(), 0)
                """, tenantId, productId, productId, uomId, tierId);

        UUID warehouseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO warehouses (id, tenant_id, code, name, is_default, is_active,
                                        created_at, updated_at, version)
                VALUES (?, ?, 'MAIN', 'Main Warehouse', true, true, now(), now(), 0)
                """, warehouseId, tenantId);

        registerId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cash_registers (id, tenant_id, code, name, warehouse_id,
                                            default_price_tier_id, receipt_width_mm, is_active,
                                            created_at, updated_at, version)
                VALUES (?, ?, 'REG-01', 'Register 1', ?, ?, 80, true, now(), now(), 0)
                """, registerId, tenantId, warehouseId, tierId);

        token = jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), tenantId, "cashier@test.local", "fr",
                Set.of(), Set.of("pos:open_session", "pos:close_session", "pos:operate")));
    }

    @Test
    @DisplayName("POST /pos/sessions returns 201 with open session")
    void openSession_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/pos/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registerId":"%s","openingFloat":0}
                                """.formatted(registerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @DisplayName("POST /pos/sales returns 201 with sale (idempotent key)")
    void createSale_returns201() throws Exception {
        String sessionJson = mockMvc.perform(post("/api/v1/pos/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registerId":"%s","openingFloat":0}
                                """.formatted(registerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID sessionId = UUID.fromString(JsonPath.read(sessionJson, "$.id").toString());
        String idemKey = "CTRL-IT-" + UUID.randomUUID();

        mockMvc.perform(post("/api/v1/pos/sales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "idempotencyKey":"%s",
                                  "registerId":"%s",
                                  "sessionId":"%s",
                                  "lines":[{"variantId":"%s","uomId":"%s","quantity":1}],
                                  "payment":{"cash":100.00}
                                }
                                """.formatted(idemKey, registerId, sessionId, productId, uomId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.total").value(100.0));
    }

    @Test
    @DisplayName("POST /pos/sales/sync returns 200 with results list")
    void syncSales_returns200() throws Exception {
        String sessionJson = mockMvc.perform(post("/api/v1/pos/sessions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"registerId":"%s","openingFloat":0}
                                """.formatted(registerId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID sessionId = UUID.fromString(JsonPath.read(sessionJson, "$.id").toString());

        mockMvc.perform(post("/api/v1/pos/sales/sync")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sales":[{
                                    "idempotencyKey":"SYNC-IT-%s",
                                    "registerId":"%s",
                                    "sessionId":"%s",
                                    "lines":[{"variantId":"%s","uomId":"%s","quantity":1}],
                                    "payment":{"cash":100.00}
                                  }]
                                }
                                """.formatted(UUID.randomUUID(), registerId, sessionId, productId, uomId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray())
                .andExpect(jsonPath("$.results[0].status").value("ACCEPTED"));
    }
}
