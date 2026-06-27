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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HisabErpApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("DeliveryController — HTTP layer")
class DeliveryControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;
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

        token = jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), tenantId, "test@test.local", "fr",
                Set.of(), Set.of("delivery:read", "delivery:create", "delivery:update", "delivery:execute")));
    }

    @Test
    @DisplayName("GET /deliveries returns 200")
    void listDeliveries_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/deliveries").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("POST /deliveries without invoiceId returns 422 (business rule: no shipping before billing)")
    void createDelivery_withoutInvoice_returns422() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/deliveries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","warehouseId":"%s","address":"Nouakchott","lines":[]}
                                """.formatted(customerId, warehouseId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /deliveries with unknown invoiceId returns 404")
    void createDelivery_unknownInvoice_returns404() throws Exception {
        UUID customerId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/deliveries")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"%s","invoiceId":"%s","warehouseId":"%s","address":"NKC","lines":[]}
                                """.formatted(customerId, invoiceId, warehouseId)))
                .andExpect(status().isNotFound());
    }
}
