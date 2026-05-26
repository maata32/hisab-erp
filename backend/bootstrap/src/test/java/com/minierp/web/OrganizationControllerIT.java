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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MiniErpApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("OrganizationController — HTTP layer (SUPER_ADMIN)")
class OrganizationControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    UUID tenantId;
    String superAdminToken;
    String tenantAdminToken;

    @BeforeEach
    void seed() {
        tenantId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           created_at, updated_at, version)
                VALUES (?, ?, 'Test Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', 'ACTIVE',
                        now(), now(), 0)
                """, tenantId, "org-" + tenantId);

        superAdminToken = jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), tenantId, "root@test.local", "fr",
                Set.of("SUPER_ADMIN"), Set.of()));

        tenantAdminToken = jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), tenantId, "admin@test.local", "fr",
                Set.of("TENANT_ADMIN"), Set.of("organization:read")));
    }

    @Test
    @DisplayName("GET /organizations as SUPER_ADMIN returns 200")
    void list_asSuperAdmin_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations").header("Authorization", "Bearer " + superAdminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /organizations as tenant admin returns 403 (cross-tenant op)")
    void list_asTenantAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/organizations").header("Authorization", "Bearer " + tenantAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /organizations/me returns 200 for any authenticated user")
    void getMe_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/me").header("Authorization", "Bearer " + tenantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tenantId.toString()));
    }
}
