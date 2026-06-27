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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HisabErpApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("UomController — HTTP layer")
class UomControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;
    UUID uomCatId;
    UUID uomId;
    String token;
    String readOnlyToken;

    @BeforeEach
    void seed() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "org-" + tenantId);

        uomCatId = UUID.randomUUID();
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

        token = jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), tenantId, "test@test.local", "fr",
                Set.of(), Set.of("uom:read", "uom:create", "uom:update", "uom:delete")));

        readOnlyToken = jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), tenantId, "viewer@test.local", "fr",
                Set.of(), Set.of("uom:read")));
    }

    /** Insert a product referencing {@code uomId} so the unit counts as "in use". */
    private void insertProductUsing(UUID baseUomId) {
        jdbc.update("""
                INSERT INTO products (id, tenant_id, sku, name, base_uom_id, default_tax_rate,
                                      tracks_lots, tracks_serial, is_sellable, is_purchasable,
                                      is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, 'Test Product', ?, 0.00, false, false, true, true,
                        true, now(), now(), 0)
                """, UUID.randomUUID(), tenantId, "SKU-" + UUID.randomUUID(), baseUomId);
    }

    @Test
    @DisplayName("GET /uoms returns 200 with non-empty array")
    void listUoms_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/uoms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("POST /uoms returns 201 with new unit")
    void createUom_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/uoms")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":"%s","code":"BOX","name":"Box","ratioToBase":12,"isBase":false}
                                """.formatted(uomCatId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.code").value("BOX"))
                .andExpect(jsonPath("$.inUse").value(false));
    }

    @Test
    @DisplayName("PUT /uoms/{id} updates name + decimals → 200")
    void updateUom_nameAndDecimals_returns200() throws Exception {
        UUID boxId = insertUom("BOX", "12", false, 0);
        mockMvc.perform(put("/api/v1/uoms/" + boxId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BOX","name":"Big Box","ratioToBase":12,"isBase":false,"decimalPlaces":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Big Box"))
                .andExpect(jsonPath("$.decimalPlaces").value(2));
    }

    @Test
    @DisplayName("PUT /uoms/{id} changes ratio on an unused unit → 200")
    void updateUom_ratioOnUnusedUnit_returns200() throws Exception {
        UUID boxId = insertUom("BOX", "12", false, 0);
        mockMvc.perform(put("/api/v1/uoms/" + boxId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BOX","name":"Box","ratioToBase":24,"isBase":false,"decimalPlaces":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratioToBase").value(24));
    }

    @Test
    @DisplayName("DELETE /uoms/{id} removes an unused unit → 204")
    void deleteUom_unused_returns204() throws Exception {
        UUID boxId = insertUom("BOX", "12", false, 0);
        mockMvc.perform(delete("/api/v1/uoms/" + boxId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /uoms/{id} of a unit used by a product → 409")
    void deleteUom_inUse_returns409() throws Exception {
        UUID caseId = insertUom("CASE", "6", false, 0);
        insertProductUsing(caseId);
        mockMvc.perform(delete("/api/v1/uoms/" + caseId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.uom.in_use"));
    }

    @Test
    @DisplayName("PUT /uoms/{id} changing ratio on a referenced unit → 422")
    void updateUom_ratioOnReferencedUnit_returns422() throws Exception {
        UUID caseId = insertUom("CASE", "6", false, 0);
        insertProductUsing(caseId);
        mockMvc.perform(put("/api/v1/uoms/" + caseId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"CASE","name":"Case","ratioToBase":8,"isBase":false,"decimalPlaces":0}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("error.uom.immutable_in_use"));
    }

    @Test
    @DisplayName("GET /uoms flags a referenced unit as inUse=true")
    void listUoms_flagsInUse() throws Exception {
        UUID caseId = insertUom("CASE", "6", false, 0);
        insertProductUsing(caseId);
        mockMvc.perform(get("/api/v1/uoms")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'CASE')].inUse").value(org.hamcrest.Matchers.contains(true)));
    }

    @Test
    @DisplayName("Category CRUD: POST → PUT → DELETE")
    void categoryCrud() throws Exception {
        UUID catId = insertCategory("LENGTH");
        mockMvc.perform(put("/api/v1/uoms/categories/" + catId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"LENGTH","name":"Length & distance"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Length & distance"));
        mockMvc.perform(delete("/api/v1/uoms/categories/" + catId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /uoms/categories/{id} with units → 409")
    void deleteCategory_withUnits_returns409() throws Exception {
        mockMvc.perform(delete("/api/v1/uoms/categories/" + uomCatId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("error.uom.category_in_use"));
    }

    @Test
    @DisplayName("PUT/DELETE without write authority → 403")
    void writeWithoutAuthority_returns403() throws Exception {
        UUID boxId = insertUom("BOX", "12", false, 0);
        mockMvc.perform(put("/api/v1/uoms/" + boxId)
                        .header("Authorization", "Bearer " + readOnlyToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"BOX","name":"Box","ratioToBase":12,"isBase":false,"decimalPlaces":0}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/uoms/" + boxId)
                        .header("Authorization", "Bearer " + readOnlyToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /uoms/convert converts within a category → 200")
    void convert_sameCategory_returns200() throws Exception {
        UUID dozenId = insertUom("DOZEN", "12", false, 0);
        mockMvc.perform(get("/api/v1/uoms/convert")
                        .param("amount", "2")
                        .param("from", dozenId.toString())
                        .param("to", uomId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(24));
    }

    @Test
    @DisplayName("GET /uoms/convert across categories → 422")
    void convert_crossCategory_returns422() throws Exception {
        UUID massCat = insertCategory("MASS");
        UUID kgId = insertUom(massCat, "KG", "1", true, 3);
        mockMvc.perform(get("/api/v1/uoms/convert")
                        .param("amount", "1")
                        .param("from", uomId.toString())
                        .param("to", kgId.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("error.uom.category_mismatch"));
    }

    // ── helpers ──────────────────────────────────────────────────────
    private UUID insertCategory(String code) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uom_categories (id, tenant_id, code, name, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, now(), now(), 0)
                """, id, tenantId, code, code);
        return id;
    }

    private UUID insertUom(String code, String ratio, boolean isBase, int decimals) {
        return insertUom(uomCatId, code, ratio, isBase, decimals);
    }

    private UUID insertUom(UUID categoryId, String code, String ratio, boolean isBase, int decimals) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO uoms (id, tenant_id, category_id, code, name, ratio_to_base, is_base,
                                  decimal_places, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, CAST(? AS numeric), ?, ?, now(), now(), 0)
                """, id, tenantId, categoryId, code, code, ratio, isBase, decimals);
        return id;
    }
}
