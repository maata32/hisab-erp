package com.minierp.web;

import com.minierp.MiniErpApplication;
import com.minierp.identity.security.JwtService;
import com.minierp.shared.security.CurrentUser;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = MiniErpApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tenant lifecycle + registration — HTTP layer")
class TenantLifecycleIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    private String superAdmin() {
        return jwtService.issueAccessToken(new CurrentUser(
                UUID.randomUUID(), UUID.randomUUID(), "root@test.local", "fr",
                Set.of("SUPER_ADMIN"), Set.of()));
    }

    private UUID starterPlanId() {
        return jdbc.queryForObject("SELECT id FROM subscription_plans WHERE code = 'STARTER'", UUID.class);
    }

    private UUID insertOrg(String status, UUID planId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO organizations (id, code, name, type, currency, locale, timezone, status,
                                           subscription_plan_id, trial_ends_at, created_at, updated_at, version)
                VALUES (?, ?, 'Lifecycle Org', 'BOUTIQUE', 'MRU', 'fr', 'Africa/Nouakchott', ?, ?,
                        now() + interval '30 days', now(), now(), 0)
                """, id, "org-" + id, status, planId);
        return id;
    }

    private String statusOf(UUID orgId) {
        return jdbc.queryForObject("SELECT status FROM organizations WHERE id = ?", String.class, orgId);
    }

    // ── Registration ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /registrations (public) creates a PENDING tenant + admin user")
    void register_createsPendingTenant() throws Exception {
        String code = "reg-" + UUID.randomUUID().toString().substring(0, 8);
        String body = """
                {"tenantCode":"%s","companyName":"Ma Boutique","companyType":"BOUTIQUE",
                 "planCode":"STARTER","locale":"fr",
                 "adminFullName":"Alice Admin","adminEmail":"alice@%s.local","password":"Sup3rPass!"}
                """.formatted(code, code);

        mockMvc.perform(post("/api/v1/registrations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.tenantCode").value(code));

        UUID orgId = jdbc.queryForObject("SELECT id FROM organizations WHERE code = ?", UUID.class, code);
        assertThat(statusOf(orgId)).isEqualTo("PENDING");
        Integer userCount = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE tenant_id = ? AND email = ?", Integer.class, orgId, "alice@" + code + ".local");
        assertThat(userCount).isEqualTo(1);
        UUID adminId = jdbc.queryForObject(
                "SELECT primary_admin_user_id FROM organizations WHERE id = ?", UUID.class, orgId);
        assertThat(adminId).isNotNull();
    }

    @Test
    @DisplayName("Login is blocked while the tenant is PENDING")
    void login_blockedForPendingTenant() throws Exception {
        String code = "pend-" + UUID.randomUUID().toString().substring(0, 8);
        String reg = """
                {"tenantCode":"%s","companyName":"Pending Co","companyType":"BOUTIQUE",
                 "planCode":"STARTER","adminFullName":"Bob","adminEmail":"bob@%s.local","password":"Sup3rPass!"}
                """.formatted(code, code);
        mockMvc.perform(post("/api/v1/registrations").contentType(MediaType.APPLICATION_JSON).content(reg))
                .andExpect(status().isAccepted());

        String login = """
                {"tenantCode":"%s","email":"bob@%s.local","password":"Sup3rPass!"}
                """.formatted(code, code);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isForbidden());
    }

    // ── Approve / reject / activate ──────────────────────────────────────────

    @Test
    @DisplayName("POST /{id}/approve moves PENDING → TRIAL and creates a subscription")
    void approve_movesToTrial() throws Exception {
        UUID orgId = insertOrg("PENDING", starterPlanId());

        mockMvc.perform(post("/api/v1/organizations/{id}/approve", orgId)
                        .header("Authorization", "Bearer " + superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRIAL"));

        assertThat(statusOf(orgId)).isEqualTo("TRIAL");
        Integer subs = jdbc.queryForObject(
                "SELECT count(*) FROM subscriptions WHERE organization_id = ? AND status = 'TRIAL'", Integer.class, orgId);
        assertThat(subs).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /{id}/reject moves PENDING → ARCHIVED")
    void reject_movesToArchived() throws Exception {
        UUID orgId = insertOrg("PENDING", starterPlanId());

        mockMvc.perform(post("/api/v1/organizations/{id}/reject", orgId)
                        .header("Authorization", "Bearer " + superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"incomplete\"}"))
                .andExpect(status().isOk());

        assertThat(statusOf(orgId)).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("POST /{id}/activate moves TRIAL → ACTIVE and activates the subscription")
    void activate_movesToActive() throws Exception {
        UUID orgId = insertOrg("TRIAL", starterPlanId());

        mockMvc.perform(post("/api/v1/organizations/{id}/activate", orgId)
                        .header("Authorization", "Bearer " + superAdmin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"billingCycle\":\"MONTHLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(statusOf(orgId)).isEqualTo("ACTIVE");
        Integer subs = jdbc.queryForObject(
                "SELECT count(*) FROM subscriptions WHERE organization_id = ? AND status = 'ACTIVE'", Integer.class, orgId);
        assertThat(subs).isEqualTo(1);
    }

    @Test
    @DisplayName("approve on a non-PENDING tenant is rejected (422)")
    void approve_nonPending_isRejected() throws Exception {
        UUID orgId = insertOrg("ACTIVE", starterPlanId());

        mockMvc.perform(post("/api/v1/organizations/{id}/approve", orgId)
                        .header("Authorization", "Bearer " + superAdmin()))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── Status filter ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /organizations?status=PENDING only returns pending tenants")
    void list_filtersByStatus() throws Exception {
        UUID pending = insertOrg("PENDING", starterPlanId());
        insertOrg("ACTIVE", starterPlanId());

        mockMvc.perform(get("/api/v1/organizations").param("status", "PENDING").param("size", "100")
                        .header("Authorization", "Bearer " + superAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + pending + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.status != 'PENDING')]").doesNotExist());
    }
}
