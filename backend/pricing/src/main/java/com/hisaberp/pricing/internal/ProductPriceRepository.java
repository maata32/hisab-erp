package com.hisaberp.pricing.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

    List<ProductPrice> findByProductId(UUID productId);

    List<ProductPrice> findByVariantId(UUID variantId);

    /**
     * Upsert key for a price row. {@code minQty} is part of the identity — a quantity
     * break (e.g. 1→100, 10→80) is a distinct row for the same variant/uom/tier/validFrom;
     * omitting it made the second break overwrite the first (BUG-13 / PRC-10). A null
     * minQty (the "base" price, qty-agnostic) is matched via a sentinel so null==null.
     */
    @Query("""
            SELECT p FROM ProductPrice p
             WHERE p.variantId = :variantId
               AND p.uomId = :uomId
               AND p.priceTierId = :tierId
               AND p.validFrom = :validFrom
               AND COALESCE(p.minQty, -1) = COALESCE(:minQty, -1)
            """)
    Optional<ProductPrice> findForUpsert(@Param("variantId") UUID variantId,
                                         @Param("uomId") UUID uomId,
                                         @Param("tierId") UUID tierId,
                                         @Param("validFrom") LocalDate validFrom,
                                         @Param("minQty") BigDecimal minQty);

    @Query("""
            SELECT p FROM ProductPrice p
             WHERE p.variantId = :variantId
               AND p.uomId = :uomId
               AND p.priceTierId = :tierId
               AND p.validFrom <= :date
               AND (p.validTo IS NULL OR p.validTo >= :date)
             ORDER BY p.validFrom DESC, p.minQty DESC NULLS LAST
            """)
    List<ProductPrice> findActiveByVariant(@Param("variantId") UUID variantId,
                                           @Param("uomId") UUID uomId,
                                           @Param("tierId") UUID tierId,
                                           @Param("date") LocalDate date);

    /** Product-level fallback used for uniform pricing and freshly generated variants. */
    @Query("""
            SELECT p FROM ProductPrice p
             WHERE p.productId = :productId
               AND p.uomId = :uomId
               AND p.priceTierId = :tierId
               AND p.validFrom <= :date
               AND (p.validTo IS NULL OR p.validTo >= :date)
             ORDER BY p.validFrom DESC, p.minQty DESC NULLS LAST
            """)
    List<ProductPrice> findActiveByProduct(@Param("productId") UUID productId,
                                           @Param("uomId") UUID uomId,
                                           @Param("tierId") UUID tierId,
                                           @Param("date") LocalDate date);
}
