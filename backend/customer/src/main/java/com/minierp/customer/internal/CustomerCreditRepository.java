package com.minierp.customer.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CustomerCreditRepository extends JpaRepository<CustomerCredit, UUID> {
    List<CustomerCredit> findByCustomerIdAndStatusOrderByCreatedAtAsc(UUID customerId, CustomerCreditStatus status);
}
