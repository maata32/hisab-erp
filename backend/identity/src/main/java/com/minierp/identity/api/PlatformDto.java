package com.minierp.identity.api;

import java.util.UUID;

/** DTOs for the cross-tenant platform (super-admin) console. */
public final class PlatformDto {

    private PlatformDto() {}

    /** Number of users in a given organization. */
    public record OrgUserCount(UUID organizationId, long userCount) {}
}
