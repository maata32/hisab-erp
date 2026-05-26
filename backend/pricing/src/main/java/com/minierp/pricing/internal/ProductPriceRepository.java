package com.minierp.pricing.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    List<ProductPrice> findByProductId(UUID productId);

    Optional<ProductPrice> findByProductIdAndUomIdAndPriceTierIdAndValidFrom(
            UUID productId, UUID uomId, UUID priceTierId, LocalDate validFrom);

    @Query("""
            SELECT p FROM ProductPrice p
             WHERE p.productId = :productId
               AND p.uomId = :uomId
               AND p.priceTierId = :tierId
               AND p.validFrom <= :date
               AND (p.validTo IS NULL OR p.validTo >= :date)
             ORDER BY p.validFrom DESC, p.minQty DESC NULLS LAST
            """)
    List<ProductPrice> findActive(@Param("productId") UUID productId,
                                  @Param("uomId") UUID uomId,
                                  @Param("tierId") UUID tierId,
                                  @Param("date") LocalDate date);
}
