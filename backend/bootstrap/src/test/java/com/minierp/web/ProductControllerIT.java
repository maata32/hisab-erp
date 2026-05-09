package com.minierp.web;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MiniErpApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ProductController — HTTP layer")
class ProductControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;
    UUID uomId;
    UUID seededProductId;
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

        uomId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name,
                                  ratio_to_base, is_base, decimal_places,
                                  created_at, updated_at, version)
                VALUES (?, ?, ?, 'PCE', 'Piece', 1, true, 0, now(), now(), 0)
                """, uomId, tenantId, uomCatId);

        seededProductId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id,
                                      default_tax_rate, tracks_lots, tracks_serial,
                                      is_sellable, is_purchasable, is_active,
                                      created_at, updated_at, version)
                VALUES (?, ?, ?, 'Seeded Product', ?, 0.00, false, false, true, true,
                        true, now(), now(), 0)
                """, seededProductId, tenantId, "SEED-" + seededProductId, uomId);

        token = jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), tenantId, "test@test.local", "fr",
                Set.of(), Set.of("product:read", "product:create")));
    }

    @Test
    @DisplayName("POST /products returns 201 with created product")
    void createProduct_returns201() throws Exception {
        String sku = "NEW-" + UUID.randomUUID();
        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"New Product","baseUomId":"%s","defaultTaxRate":0}
                                """.formatted(sku, uomId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.sku").value(sku));
    }

    @Test
    @DisplayName("GET /products/{id} returns 200 with product")
    void getProduct_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/products/{id}", seededProductId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(seededProductId.toString()));
    }
}
