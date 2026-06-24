package com.minierp.pos.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.catalog.api.ProductSnapshot;
import com.minierp.catalog.api.VariantLookup;
import com.minierp.catalog.api.VariantView;
import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.lotexpiry.api.LotAllocation;
import com.minierp.lotexpiry.api.LotOperations;
import com.minierp.pos.api.CashRegisterDto;
import com.minierp.pos.api.CashSessionDto;
import com.minierp.pos.api.CreateSaleRequest;
import com.minierp.pos.api.SaleDto;
import com.minierp.pos.api.SaleDto.SaleLineDto;
import com.minierp.pos.api.SyncSalesResponse;
import com.minierp.pricing.api.PriceResolver;
import com.minierp.pricing.api.ResolvedPrice;
import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.ConflictException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.persistence.TenantGuard;
import com.minierp.shared.util.PageResponse;
import com.minierp.treasury.api.TreasuryOperations;
import com.minierp.uom.api.UomLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PosService {

    private static final ZoneId TZ = ZoneId.of("Africa/Nouakchott");

    private final CashRegisterRepository registers;
    private final CashSessionRepository sessions;
    private final SaleRepository sales;
    private final SaleLineRepository saleLines;
    private final CashMovementRepository cashMovements;
    private final SaleNumberSequenceRepository sequences;

    private final CatalogLookup catalog;
    private final VariantLookup variants;
    private final UomLookup uomLookup;
    private final PriceResolver priceResolver;
    private final StockOperations stockOps;
    private final LotOperations lotOps;
    private final TreasuryOperations treasury;

    // Self-reference (proxy) so syncSales can invoke createSale through its @Transactional
    // boundary — each batched sale gets its own transaction. @Lazy breaks the construction cycle.
    @Autowired @Lazy
    private PosService self;

    // ── Registers ───────────────────────────────────────────────────────────

    @Transactional
    public CashRegisterDto createRegister(CreateRegisterRequest req) {
        if (registers.existsByCode(req.code())) {
            throw new ConflictException("error.data_integrity",
                    Map.of("field", "code", "value", req.code()));
        }
        CashRegister r = CashRegister.builder()
                .code(req.code())
                .name(req.name())
                .warehouseId(req.warehouseId())
                .defaultPriceTierId(req.defaultPriceTierId())
                .receiptWidthMm(req.receiptWidthMm() == null ? 80 : req.receiptWidthMm())
                .build();
        registers.save(r);
        return toRegisterDto(r);
    }

    @Transactional(readOnly = true)
    public List<CashRegisterDto> listRegisters() {
        return registers.findByActiveTrue().stream().map(this::toRegisterDto).toList();
    }

    @Transactional(readOnly = true)
    public CashRegisterDto getRegister(UUID registerId) {
        return toRegisterDto(loadRegisterInTenant(registerId));
    }

    // ── Sessions ─────────────────────────────────────────────────────────────

    /**
     * Cashier opens a session. The opening float is always 0 per the simplified model:
     * the cashier may informally lend personal change, but no money is transferred from
     * the vault until validation.
     */
    @Transactional
    public CashSessionDto openSession(UUID registerId, BigDecimal openingFloat, UUID userId) {
        CashRegister register = loadRegisterInTenant(registerId);
        if (!register.isActive()) {
            throw new BusinessException("error.pos.register_inactive", Map.of("registerId", registerId));
        }
        if (sessions.findOpen(registerId).isPresent()) {
            throw new ConflictException("error.pos.session_already_open", Map.of("registerId", registerId));
        }
        // Always 0 — sessions start empty; vault is touched only at validation.
        CashSession session = CashSession.builder()
                .registerId(registerId)
                .cashierUserId(userId)
                .openedAt(Instant.now())
                .openingFloat(BigDecimal.ZERO)
                .build();
        sessions.save(session);

        CashMovement movement = CashMovement.builder()
                .sessionId(session.getId())
                .type(CashMovementType.OPENING_FLOAT)
                .amount(BigDecimal.ZERO)
                .reason("Opening float")
                .userId(userId)
                .build();
        cashMovements.save(movement);

        return toSessionDto(session);
    }

    /**
     * Cashier closes a session. Records the counted cash + discrepancy and marks the
     * session as CLOSED. The vault is NOT credited here — a vault manager must
     * validate the deposit separately via {@link #validateSession}.
     */
    @Transactional
    public CashSessionDto closeSession(UUID sessionId, BigDecimal countedClosing, String note, UUID userId) {
        CashSession session = lockSessionInTenant(sessionId);
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new BusinessException("error.pos.session_not_open", Map.of("sessionId", sessionId));
        }
        // Expected cash = opening float (always 0) + net cash from sales.
        BigDecimal expected = session.getOpeningFloat()
                .add(session.getTotalCashIn())
                .subtract(session.getTotalCashOut());
        BigDecimal counted = countedClosing == null ? expected : countedClosing;
        BigDecimal difference = counted.subtract(expected);

        session.setClosedAt(Instant.now());
        session.setExpectedClosing(expected);
        session.setCountedClosing(counted);
        session.setDifference(difference);
        session.setNote(note);

        if (difference.abs().compareTo(BigDecimal.ZERO) > 0) {
            CashMovement reconciliation = CashMovement.builder()
                    .sessionId(sessionId)
                    .type(CashMovementType.CLOSING_RECONCILIATION)
                    .amount(difference)
                    .reason("Closing difference")
                    .userId(userId)
                    .build();
            cashMovements.save(reconciliation);
        }

        // Zero-cash sessions (no sales) need no vault validation — auto-validate.
        if (expected.signum() == 0 && counted.signum() == 0) {
            session.setStatus(CashSessionStatus.VALIDATED);
            session.setValidatedAt(Instant.now());
            session.setValidatedBy(userId);
        } else {
            session.setStatus(CashSessionStatus.CLOSED);
        }

        return toSessionDto(session);
    }

    /**
     * Vault manager confirms physical receipt of the cashier's deposit. Credits the
     * vault with {@code countedClosing} and marks the session as VALIDATED. Idempotent
     * per session (the check on status prevents double-validation).
     */
    @Transactional
    public CashSessionDto validateSession(UUID sessionId, UUID userId) {
        CashSession session = lockSessionInTenant(sessionId);
        if (session.getStatus() != CashSessionStatus.CLOSED) {
            throw new BusinessException("error.pos.session_not_validatable",
                    Map.of("sessionId", sessionId, "status", session.getStatus().name()));
        }
        BigDecimal counted = session.getCountedClosing() == null ? BigDecimal.ZERO : session.getCountedClosing();
        treasury.depositFromPosSession(sessionId, counted, userId);
        session.setStatus(CashSessionStatus.VALIDATED);
        session.setValidatedAt(Instant.now());
        session.setValidatedBy(userId);
        return toSessionDto(session);
    }

    @Transactional(readOnly = true)
    public java.util.List<CashSessionDto> listPendingValidations() {
        return sessions.findPendingValidation().stream()
                .map(this::toSessionDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<CashSessionDto> listMyPendingSessions(UUID cashierId) {
        return sessions.findPendingByCashier(cashierId).stream()
                .map(this::toSessionDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<CashSessionDto> listMyValidatedSessions(UUID cashierId, java.time.LocalDate date) {
        java.time.Instant from = date.atStartOfDay(TZ).toInstant();
        java.time.Instant to = date.plusDays(1).atStartOfDay(TZ).toInstant();
        return sessions.findValidatedByCashierBetween(cashierId, from, to).stream()
                .map(this::toSessionDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CashSessionDto getSession(UUID sessionId) {
        return toSessionDto(loadSessionInTenant(sessionId));
    }

    // ── Sales ────────────────────────────────────────────────────────────────

    @Transactional
    public SaleDto createSale(CreateSaleRequest req, UUID userId) {
        // Idempotency: return existing sale if key already used
        var existing = sales.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            log.debug("Idempotent replay for key={}", req.idempotencyKey());
            return toDto(existing.get());
        }

        CashRegister register = loadRegisterInTenant(req.registerId());
        if (!register.isActive()) {
            throw new BusinessException("error.pos.register_inactive", Map.of());
        }

        CashSession session = lockSessionInTenant(req.sessionId());
        if (!req.registerId().equals(session.getRegisterId())) {
            throw new BusinessException("error.pos.session_register_mismatch", Map.of());
        }
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new BusinessException("error.pos.session_not_open", Map.of());
        }

        LocalDate saleDate = req.occurredAt() == null
                ? LocalDate.now(TZ)
                : req.occurredAt().atZone(TZ).toLocalDate();

        // Build lines and compute totals
        List<SaleLine> builtLines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        BigDecimal discountTotal = BigDecimal.ZERO;

        for (int i = 0; i < req.lines().size(); i++) {
            var lr = req.lines().get(i);
            VariantView variant = variants.require(lr.variantId());

            UUID effectiveUomId = lr.uomId() != null ? lr.uomId() : variant.baseUomId();
            ResolvedPrice price = priceResolver.resolve(
                    lr.variantId(), effectiveUomId,
                    register.getDefaultPriceTierId(),
                    lr.quantity(), saleDate, lr.unitDiscount());

            BigDecimal baseQty = effectiveUomId.equals(variant.baseUomId())
                    ? lr.quantity()
                    : uomLookup.convert(lr.quantity(), effectiveUomId, variant.baseUomId());

            BigDecimal lineDiscount = lr.unitDiscount() != null ? lr.unitDiscount() : BigDecimal.ZERO;

            SaleLine line = SaleLine.builder()
                    .lineNumber(i + 1)
                    .variantId(variant.id())
                    .productId(variant.productId())
                    .uomId(effectiveUomId)
                    .quantity(lr.quantity())
                    .baseQuantity(baseQty.setScale(6, RoundingMode.HALF_UP))
                    .unitPrice(price.unitPrice())
                    .unitDiscount(lineDiscount)
                    .taxRate(price.taxRate())
                    .subtotal(price.subtotal())
                    .taxAmount(price.taxAmount())
                    .total(price.total())
                    .taxInclusive(price.taxInclusive())
                    .snapshotName(variant.displayLabel())
                    .snapshotSku(variant.sku())
                    .build();
            builtLines.add(line);

            subtotal = subtotal.add(price.subtotal());
            taxTotal = taxTotal.add(price.taxAmount());
            discountTotal = discountTotal.add(lineDiscount.multiply(lr.quantity())
                    .setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal total = subtotal.add(taxTotal);
        BigDecimal paidCash = safe(req.payment() == null ? null : req.payment().cash());
        BigDecimal paidCard = safe(req.payment() == null ? null : req.payment().card());
        BigDecimal paidMobile = safe(req.payment() == null ? null : req.payment().mobile());
        BigDecimal paidCredit = safe(req.payment() == null ? null : req.payment().credit());
        BigDecimal totalPaid = paidCash.add(paidCard).add(paidMobile).add(paidCredit);
        BigDecimal changeDue = totalPaid.subtract(total).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        String number = allocateNumber();
        Instant completedAt = req.occurredAt() != null ? req.occurredAt() : Instant.now();

        Sale sale = Sale.builder()
                .idempotencyKey(req.idempotencyKey())
                .number(number)
                .registerId(req.registerId())
                .sessionId(req.sessionId())
                .warehouseId(register.getWarehouseId())
                .cashierUserId(userId)
                .partyId(req.customerId())
                .subtotal(subtotal)
                .taxAmount(taxTotal)
                .discountAmount(discountTotal)
                .total(total)
                .paidCash(paidCash)
                .paidCard(paidCard)
                .paidMobile(paidMobile)
                .paidCredit(paidCredit)
                .changeDue(changeDue)
                .completedAt(completedAt)
                .note(req.note())
                .build();
        sales.save(sale);

        for (SaleLine line : builtLines) {
            line.setSaleId(sale.getId());
            saleLines.save(line);
        }

        // Update session running totals.
        // totalCashIn tracks NET cash kept in till (tendered minus change given back),
        // since only that amount physically remains in the drawer.
        session.setTotalSales(session.getTotalSales().add(total));
        session.setTotalCashIn(session.getTotalCashIn().add(paidCash.subtract(changeDue)));

        // Decrement stock per line — permit negative per spec §3.1.3.
        // builtLines is in the same order as req.lines(), so we index back to pick up the
        // optional manual lot selection.
        for (int i = 0; i < builtLines.size(); i++) {
            SaleLine line = builtLines.get(i);
            var lr = req.lines().get(i);
            stockOps.issueAllowNegative(
                    register.getWarehouseId(), line.getVariantId(), line.getBaseQuantity(),
                    StockMovementType.SALE, "SALE", sale.getId(), sale.getNumber(),
                    null, userId);
            if (lr.lotAllocations() != null && !lr.lotAllocations().isEmpty()) {
                // Manual lot selection (LOT-15): consume exactly the designated lots, overriding FEFO.
                List<LotAllocation> allocs = lr.lotAllocations().stream()
                        .map(a -> new LotAllocation(a.lotId(), a.quantity())).toList();
                lotOps.consumeExplicitLots(line.getVariantId(), register.getWarehouseId(),
                        line.getBaseQuantity(), allocs, "POS_SALE", sale.getId());
            } else {
                // Automatic FEFO consumption for lot/expiry-tracked variants (no-op otherwise).
                lotOps.consumeFefoIfTracked(line.getVariantId(), register.getWarehouseId(),
                        line.getBaseQuantity(), "POS_SALE", sale.getId());
            }
        }

        return toDto(sale);
    }

    // NOT @Transactional: each sale is processed in its OWN transaction via self.createSale (the
    // proxy applies createSale's @Transactional). A sale that fails rolls back only its own
    // transaction — the others still commit. (Previously this method was @Transactional and a single
    // failing sale marked the shared transaction rollback-only, 500-ing the whole /sync batch.)
    public SyncSalesResponse syncSales(List<CreateSaleRequest> batch, UUID userId) {
        List<SyncSalesResponse.SyncResult> results = new ArrayList<>();
        for (CreateSaleRequest req : batch) {
            try {
                SaleDto dto = self.createSale(req, userId);
                results.add(new SyncSalesResponse.SyncResult(
                        req.idempotencyKey(), dto.id(), dto.number(), "ACCEPTED", null));
            } catch (Exception ex) {
                log.warn("Sync failed for idempotencyKey={}: {}", req.idempotencyKey(), ex.getMessage());
                results.add(new SyncSalesResponse.SyncResult(
                        req.idempotencyKey(), null, null, "ERROR", ex.getMessage()));
            }
        }
        return new SyncSalesResponse(results);
    }

    @Transactional(readOnly = true)
    public SaleDto getSale(UUID saleId) {
        return toDto(loadSaleInTenant(saleId));
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleDto> listSalesBySession(UUID sessionId, Pageable pageable) {
        return PageResponse.of(
                sales.findBySessionIdOrderByCompletedAtDesc(sessionId, pageable).map(this::toDto));
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleDto> listSalesByRegister(UUID registerId, Instant from, Instant to, Pageable pageable) {
        return PageResponse.of(
                sales.findByRegisterIdAndCompletedAtBetween(registerId, from, to, pageable).map(this::toDto));
    }

    // ── Void / Refund ────────────────────────────────────────────────────────

    /** Cancel a sale that was just made on the SAME open session — reverses stock and totals. */
    @Transactional
    public SaleDto voidSale(UUID saleId, String reason, UUID userId) {
        Sale sale = loadSaleInTenant(saleId);
        if (sale.getStatus() != SaleStatus.COMPLETED) {
            throw new BusinessException("error.pos.sale.not_voidable",
                    Map.of("status", sale.getStatus().name()));
        }
        CashSession session = sessions.lockById(sale.getSessionId())
                .orElseThrow(() -> NotFoundException.of("entity.cash_session", sale.getSessionId()));
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new BusinessException("error.pos.sale.session_closed", Map.of());
        }

        // Reverse stock at the current CMP (kind = SALE_RETURN — does not move CMP).
        List<SaleLine> lines = saleLines.findBySaleIdOrderByLineNumberAsc(saleId);
        for (SaleLine line : lines) {
            BigDecimal cmp = stockOps.getStock(sale.getWarehouseId(), line.getVariantId())
                    .averageCost();
            stockOps.adjust(sale.getWarehouseId(), line.getVariantId(),
                    line.getBaseQuantity(),
                    cmp == null ? BigDecimal.ZERO : cmp,
                    StockMovementType.SALE_RETURN,
                    "VOID " + sale.getNumber(), userId);
            // Restore consumed lots for lot/expiry-tracked variants (no-op otherwise).
            lotOps.restoreLotsOnReturn(line.getProductId(), line.getVariantId(), sale.getWarehouseId(),
                    line.getBaseQuantity(), "POS_VOID", sale.getId());
        }

        // Reverse session totals (subtract sale total + the NET cash that was kept).
        BigDecimal voidNetCash = safe(sale.getPaidCash()).subtract(safe(sale.getChangeDue()));
        session.setTotalSales(safe(session.getTotalSales()).subtract(sale.getTotal()));
        session.setTotalCashIn(safe(session.getTotalCashIn()).subtract(voidNetCash));

        sale.setStatus(SaleStatus.CANCELLED);
        sale.setVoidedAt(Instant.now());
        sale.setVoidedBy(userId);
        sale.setVoidReason(reason);
        return toDto(sale);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Tenant-guarded by-id loads: findById/lockById bypass the Hibernate tenant filter.
    private CashRegister loadRegisterInTenant(UUID registerId) {
        return TenantGuard.requireSameTenant(registers.findById(registerId),
                () -> NotFoundException.of("entity.cash_register", registerId));
    }

    private CashSession loadSessionInTenant(UUID sessionId) {
        return TenantGuard.requireSameTenant(sessions.findById(sessionId),
                () -> NotFoundException.of("entity.cash_session", sessionId));
    }

    private CashSession lockSessionInTenant(UUID sessionId) {
        return TenantGuard.requireSameTenant(sessions.lockById(sessionId),
                () -> NotFoundException.of("entity.cash_session", sessionId));
    }

    private Sale loadSaleInTenant(UUID saleId) {
        return TenantGuard.requireSameTenant(sales.findById(saleId),
                () -> NotFoundException.of("entity.sale", saleId));
    }

    private String allocateNumber() {
        int year = Year.now().getValue();
        SaleNumberSequence seq = sequences.lockByYear(year)
                .orElseGet(() -> sequences.save(SaleNumberSequence.forYear(year)));
        seq.setCounter(seq.getCounter() + 1);
        return String.format("S-%d-%06d", year, seq.getCounter());
    }

    private void requireOpenSession(CashSession session) {
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new BusinessException("error.pos.session_not_open",
                    Map.of("sessionId", session.getId()));
        }
    }

    private static BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private SaleDto toDto(Sale s) {
        var lines = saleLines.findBySaleIdOrderByLineNumberAsc(s.getId()).stream()
                .map(l -> new SaleLineDto(l.getId(), l.getLineNumber(), l.getVariantId(), l.getProductId(),
                        l.getUomId(), l.getQuantity(), l.getBaseQuantity(),
                        l.getUnitPrice(), l.getUnitDiscount(), l.getTaxRate(),
                        l.getSubtotal(), l.getTaxAmount(), l.getTotal(),
                        l.isTaxInclusive(), l.getSnapshotName(), l.getSnapshotSku()))
                .toList();
        return new SaleDto(s.getId(), s.getNumber(), s.getIdempotencyKey(),
                s.getRegisterId(), s.getSessionId(), s.getWarehouseId(),
                s.getCashierUserId(), s.getPartyId(), s.getStatus().name(),
                s.getCurrency(), s.getSubtotal(), s.getTaxAmount(), s.getDiscountAmount(),
                s.getTotal(), s.getPaidCash(), s.getPaidCard(), s.getPaidMobile(),
                s.getPaidCredit(), s.getChangeDue(), s.getCompletedAt(), s.getNote(),
                s.getVoidedAt(), s.getVoidReason(), lines);
    }

    @Transactional(readOnly = true)
    public BigDecimal salesToday() {
        Instant startOfDay = LocalDate.now(TZ).atStartOfDay(TZ).toInstant();
        Instant endOfDay = LocalDate.now(TZ).plusDays(1).atStartOfDay(TZ).toInstant();
        return sales.sumTotalBetween(startOfDay, endOfDay);
    }

    private CashSessionDto toSessionDto(CashSession s) {
        // Same formula as closeSession: only cash flows affect the till.
        BigDecimal expectedClosing = s.getStatus() == CashSessionStatus.OPEN
                ? s.getOpeningFloat().add(s.getTotalCashIn()).subtract(s.getTotalCashOut())
                : s.getExpectedClosing();
        return new CashSessionDto(s.getId(), s.getRegisterId(), s.getCashierUserId(),
                s.getStatus().name(), s.getOpenedAt(), s.getClosedAt(),
                s.getOpeningFloat(), expectedClosing, s.getCountedClosing(),
                s.getDifference(), s.getTotalSales(), s.getTotalCashIn(),
                s.getTotalCashOut(), s.getNote(),
                s.getValidatedAt(), s.getValidatedBy());
    }

    private CashRegisterDto toRegisterDto(CashRegister r) {
        return new CashRegisterDto(r.getId(), r.getCode(), r.getName(),
                r.getWarehouseId(), r.getDefaultPriceTierId(), r.getReceiptWidthMm(), r.isActive());
    }

    // ── Request records ────────────────────────────────────────────────────────

    public record CreateRegisterRequest(
            String code,
            String name,
            UUID warehouseId,
            UUID defaultPriceTierId,
            Integer receiptWidthMm) {}
}
