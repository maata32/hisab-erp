package com.hisaberp.catalog.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, UUID> {
    List<ProductAttributeValue> findByProductId(UUID productId);

    /**
     * Bulk delete (immediate SQL) rather than a derived delete: setAttributeValues
     * re-inserts the kept (product_id, attribute_value_id) pairs right after, and a
     * derived delete defers its row deletes until flush — which Hibernate orders AFTER
     * inserts, so re-applying an existing value tripped the unique constraint → 409
     * and rolled back the whole regeneration (BUG-12 / VAR-04).
     */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM ProductAttributeValue p WHERE p.productId = :productId")
    void deleteByProductId(@Param("productId") UUID productId);

    boolean existsByAttributeValueId(UUID attributeValueId);
}
