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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = HisabErpApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("StockTransferController — HTTP layer")
class StockTransferControllerIT {

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
                Set.of(), Set.of("stock:read", "inventory:transfer")));
    }

    @Test
    @DisplayName("GET /inventory/transfers returns 200")
    void listTransfers_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/transfers").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
