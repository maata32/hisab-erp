package com.minierp.customer.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface CustomerCreditUsageRepository extends JpaRepository<CustomerCreditUsage, UUID> {
}
