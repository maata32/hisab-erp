package com.minierp.delivery.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface DeliveryLineRepository extends JpaRepository<DeliveryLine, UUID> {
    List<DeliveryLine> findByDeliveryId(UUID deliveryId);
}
