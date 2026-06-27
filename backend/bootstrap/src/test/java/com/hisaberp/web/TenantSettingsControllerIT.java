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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@SpringBootTest(classes = HisabErpApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TenantSettingsController — HTTP layer")
class TenantSettingsControllerIT {

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

        jdbc.update("""
                INSERT INTO tenant_settings (id, organization_id, created_at, updated_at, version)
                VALUES (uuid_generate_v4(), ?, now(), now(), 0)
                """, tenantId);

        token = jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), tenantId, "test@test.local", "fr",
                Set.of(), Set.of("tenant_settings:read")));
    }

    @Test
    @DisplayName("GET /settings returns 200 (isAuthenticated is enough)")
    void getSettings_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /settings persists invoiceSettings to the DB (survives reload)")
    void putSettings_persistsInvoiceSettings() throws Exception {
        String updateToken = jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), tenantId, "test@test.local", "fr",
                Set.of(), Set.of("tenant_settings:update")));

        mockMvc.perform(put("/api/v1/settings")
                        .header("Authorization", "Bearer " + updateToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"invoiceSettings\":{\"taxEnabled\":false,\"numberPrefix\":\"INV-TEST\"}}"))
                .andExpect(status().isOk());

        // Re-read straight from the table: this is what a hard reload's GET would see.
        String json = jdbc.queryForObject(
                "SELECT invoice_settings::text FROM tenant_settings WHERE organization_id = ?",
                String.class, tenantId);
        assertThat(json).contains("INV-TEST").contains("false");
    }
}
