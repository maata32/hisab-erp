package com.hisaberp.uom.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UomRepository extends JpaRepository<Uom, UUID> {
    Optional<Uom> findByCode(String code);
    List<Uom> findByCategoryIdOrderByRatioToBaseAsc(UUID categoryId);
    boolean existsByCode(String code);
    Optional<Uom> findByCategoryIdAndIsBaseTrue(UUID categoryId);
    boolean existsByCategoryId(UUID categoryId);
    long countByCategoryId(UUID categoryId);

    /**
     * True if the unit is referenced by any product, packaging, price, or document line.
     * Cross-module native query (no Java dependency on those modules — mirrors the
     * reporting module's approach); RLS scopes every sub-select to the current tenant.
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM products                  WHERE base_uom_id = :id
                UNION ALL SELECT 1 FROM product_packagings        WHERE uom_id = :id
                UNION ALL SELECT 1 FROM product_prices            WHERE uom_id = :id
                UNION ALL SELECT 1 FROM sale_lines                WHERE uom_id = :id
                UNION ALL SELECT 1 FROM stock_transfer_lines      WHERE uom_id = :id
                UNION ALL SELECT 1 FROM inventory_count_lines     WHERE uom_id = :id
                UNION ALL SELECT 1 FROM product_lots              WHERE uom_id = :id
                UNION ALL SELECT 1 FROM lot_movements             WHERE uom_id = :id
                UNION ALL SELECT 1 FROM quote_lines               WHERE uom_id = :id
                UNION ALL SELECT 1 FROM invoice_lines             WHERE uom_id = :id
                UNION ALL SELECT 1 FROM credit_note_lines         WHERE uom_id = :id
                UNION ALL SELECT 1 FROM purchase_order_lines      WHERE uom_id = :id
                UNION ALL SELECT 1 FROM purchase_invoice_lines    WHERE uom_id = :id
                UNION ALL SELECT 1 FROM goods_receipt_lines       WHERE uom_id = :id
                UNION ALL SELECT 1 FROM purchase_credit_note_lines WHERE uom_id = :id
            )
            """, nativeQuery = true)
    boolean isReferenced(@Param("id") UUID id);

    /**
     * Distinct ids of all units referenced anywhere, in a single round-trip, so the
     * list endpoint can flag each row's {@code inUse} without N per-row checks.
     */
    @Query(value = """
            SELECT DISTINCT ref_id FROM (
                SELECT base_uom_id AS ref_id FROM products
                UNION ALL SELECT uom_id FROM product_packagings
                UNION ALL SELECT uom_id FROM product_prices
                UNION ALL SELECT uom_id FROM sale_lines
                UNION ALL SELECT uom_id FROM stock_transfer_lines
                UNION ALL SELECT uom_id FROM inventory_count_lines
                UNION ALL SELECT uom_id FROM product_lots
                UNION ALL SELECT uom_id FROM lot_movements
                UNION ALL SELECT uom_id FROM quote_lines
                UNION ALL SELECT uom_id FROM invoice_lines
                UNION ALL SELECT uom_id FROM credit_note_lines
                UNION ALL SELECT uom_id FROM purchase_order_lines
                UNION ALL SELECT uom_id FROM purchase_invoice_lines
                UNION ALL SELECT uom_id FROM goods_receipt_lines
                UNION ALL SELECT uom_id FROM purchase_credit_note_lines
            ) refs
            """, nativeQuery = true)
    List<UUID> findReferencedUomIds();
}
