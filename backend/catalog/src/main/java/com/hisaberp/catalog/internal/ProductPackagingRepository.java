package com.hisaberp.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ProductPackagingRepository extends JpaRepository<ProductPackaging, UUID> {

    List<ProductPackaging> findByProductId(UUID productId);

    void deleteByProductId(UUID productId);
}
