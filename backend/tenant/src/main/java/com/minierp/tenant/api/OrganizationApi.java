package com.minierp.tenant.api;

import java.util.UUID;

public interface OrganizationApi {

    /** Direct creation by a SUPER_ADMIN — starts in TRIAL. */
    OrganizationDto create(CreateOrganizationRequest req);

    /** Self-service registration — creates the organization in PENDING status. */
    OrganizationDto register(RegisterOrganizationRequest req);

    /** Links the admin user created at registration so it can be promoted on approval. */
    void setPrimaryAdmin(UUID organizationId, UUID userId);
}
