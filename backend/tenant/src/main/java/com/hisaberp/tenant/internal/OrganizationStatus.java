package com.hisaberp.tenant.internal;

enum OrganizationStatus {
    /** Self-service registration submitted, awaiting SUPER_ADMIN approval. */
    PENDING,
    TRIAL, ACTIVE, PAST_DUE, SUSPENDED, ARCHIVED
}
