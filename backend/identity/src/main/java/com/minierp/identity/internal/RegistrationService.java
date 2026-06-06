package com.minierp.identity.internal;

import com.minierp.identity.api.RegistrationController.RegisterRequest;
import com.minierp.identity.security.PasswordHasher;
import com.minierp.tenant.api.OrganizationApi;
import com.minierp.tenant.api.OrganizationDto;
import com.minierp.tenant.api.RegisterOrganizationRequest;
import com.minierp.tenant.events.TenantRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates self-service tenant registration. Lives in identity because it
 * creates the admin {@link User} (identity-owned) alongside the organization
 * (created via the tenant module's {@link OrganizationApi}).
 *
 * <p>Runs without a tenant context: the request is unauthenticated, so
 * {@code app.current_tenant} stays NULL and RLS is bypassed — which is exactly
 * what we need to insert rows for a brand-new tenant.</p>
 */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final OrganizationApi organizationApi;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final ApplicationEventPublisher events;

    @Transactional
    public RegistrationResult register(RegisterRequest req) {
        // 1. Create the organization in PENDING status (also seeds default roles async).
        OrganizationDto org = organizationApi.register(new RegisterOrganizationRequest(
                req.tenantCode(), req.companyName(), req.companyType(),
                req.currency(), req.locale(), req.timezone(),
                req.adminEmail(), req.companyPhone(), req.companyAddress(),
                req.planCode()));

        // 2. Create the admin user — no roles yet; TENANT_ADMIN is granted on approval.
        String lang = req.locale() == null || req.locale().isBlank() ? "fr" : req.locale();
        User admin = User.builder()
                .email(req.adminEmail())
                .fullName(req.adminFullName())
                .phone(req.adminPhone())
                .preferredLanguage(lang)
                .passwordHash(hasher.hash(req.password()))
                .active(true)
                .build();
        admin.setTenantId(org.id());
        users.save(admin);

        // 3. Link the admin so it can be promoted when a super-admin approves.
        organizationApi.setPrimaryAdmin(org.id(), admin.getId());

        // 4. Notify (e-mail "request received").
        events.publishEvent(new TenantRegisteredEvent(
                org.id(), org.code(), org.name(),
                req.adminEmail(), req.adminFullName(), lang, Instant.now()));

        return new RegistrationResult(org.id(), org.code(), org.status());
    }

    public record RegistrationResult(UUID organizationId, String tenantCode, String status) {}
}
