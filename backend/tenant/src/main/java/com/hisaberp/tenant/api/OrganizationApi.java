package com.hisaberp.tenant.api;

import java.util.UUID;

public interface OrganizationApi {

    /**
     * Reserved organization that homes platform (super-admin) accounts. It is not a real
     * business tenant and is hidden from the super-admin console listing.
     */
    String PLATFORM_ORG_CODE = "__platform__";

    /** Direct creation by a SUPER_ADMIN — starts in TRIAL. */
    OrganizationDto create(CreateOrganizationRequest req);

    /** Self-service registration — creates the organization in PENDING status. */
    OrganizationDto register(RegisterOrganizationRequest req);

    /** Links the admin user created at registration so it can be promoted on approval. */
    void setPrimaryAdmin(UUID organizationId, UUID userId);
}
