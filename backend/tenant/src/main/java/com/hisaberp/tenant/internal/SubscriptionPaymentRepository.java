package com.hisaberp.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, UUID> {
    List<SubscriptionPayment> findAllByOrganizationIdOrderByPaidAtDescCreatedAtDesc(UUID organizationId);

    /** Global ledger (super-admin), most recent first. */
    List<SubscriptionPayment> findAllByOrderByPaidAtDescCreatedAtDesc();
}
