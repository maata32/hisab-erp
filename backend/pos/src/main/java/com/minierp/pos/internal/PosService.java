package com.minierp.pos.internal;

import com.minierp.catalog.api.CatalogLookup;
import com.minierp.catalog.api.ProductSnapshot;
import com.minierp.inventory.api.StockMovementType;
import com.minierp.inventory.api.StockOperations;
import com.minierp.pos.api.CashMovementDto;
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
import com.minierp.shared.util.PageResponse;
import com.minierp.uom.api.UomLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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
    private final UomLookup uomLookup;
    private final PriceResolver priceResolver;
    private final StockOperations stockOps;

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

    // ── Sessions ─────────────────────────────────────────────────────────────

    @Transactional
    public CashSessionDto openSession(UUID registerId, BigDecimal openingFloat, UUID userId) {
        CashRegister register = registers.findById(registerId)
                .orElseThrow(() -> NotFoundException.of("entity.cash_register", registerId));
        if (!register.isActive()) {
            throw new BusinessException("error.pos.register_inactive", Map.of("registerId", registerId));
        }
        if (sessions.findOpen(registerId).isPresent()) {
            throw new ConflictException("error.pos.session_already_open", Map.of("registerId", registerId));
        }
        BigDecimal safeFloat = openingFloat == null ? BigDecimal.ZERO : openingFloat;
        CashSession session = CashSession.builder()
                .registerId(registerId)
                .cashierUserId(userId)
                .openedAt(Instant.now())
                .openingFloat(safeFloat)
                .build();
        sessions.save(session);

        CashMovement movement = CashMovement.builder()
                .sessionId(session.getId())
                .type(CashMovementType.OPENING_FLOAT)
                .amount(safeFloat)
                .reason("Opening float")
                .userId(userId)
                .build();
        cashMovements.save(movement);

        return toSessionDto(session);
    }

    @Transactional
    public CashSessionDto closeSession(UUID sessionId, BigDecimal countedClosing, String note, UUID userId) {
        CashSession session = sessions.lockById(sessionId)
                .orElseThrow(() -> NotFoundException.of("entity.cash_session", sessionId));
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new BusinessException("error.pos.session_not_open", Map.of("sessionId", sessionId));
        }
        BigDecimal expected = session.getOpeningFloat()
                .add(session.getTotalSales())
                .add(session.getTotalCashIn())
                .subtract(session.getTotalCashOut());
        BigDecimal counted = countedClosing == null ? expected : countedClosing;
        BigDecimal difference = counted.subtract(expected);

        session.setStatus(CashSessionStatus.CLOSED);
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
        return toSessionDto(session);
    }

    @Transactional(readOnly = true)
    public CashSessionDto getSession(UUID sessionId) {
        return toSessionDto(sessions.findById(sessionId)
                .orElseThrow(() -> NotFoundException.of("entity.cash_session", sessionId)));
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

        CashRegister register = registers.findById(req.registerId())
                .orElseThrow(() -> NotFoundException.of("entity.cash_register", req.registerId()));
        if (!register.isActive()) {
            throw new BusinessException("error.pos.register_inactive", Map.of());
        }

        CashSession session = sessions.lockById(req.sessionId())
                .orElseThrow(() -> NotFoundException.of("entity.cash_session", req.sessionId()));
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
            ProductSnapshot product = catalog.findProductById(lr.productId())
                    .orElseThrow(() -> NotFoundException.of("entity.product", lr.productId()));

            UUID effectiveUomId = lr.uomId() != null ? lr.uomId() : product.baseUomId();
            ResolvedPrice price = priceResolver.resolve(
                    lr.productId(), effectiveUomId,
                    register.getDefaultPriceTierId(),
                    lr.quantity(), saleDate, lr.unitDiscount());

            BigDecimal baseQty = effectiveUomId.equals(product.baseUomId())
                    ? lr.quantity()
                    : uomLookup.convert(lr.quantity(), effectiveUomId, product.baseUomId());

            BigDecimal lineDiscount = lr.unitDiscount() != null ? lr.unitDiscount() : BigDecimal.ZERO;

            SaleLine line = SaleLine.builder()
                    .lineNumber(i + 1)
                    .productId(lr.productId())
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
                    .snapshotName(product.name())
                    .snapshotSku(product.sku())
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
                .customerId(req.customerId())
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

        // Update session running totals
        session.setTotalSales(session.getTotalSales().add(total));
        session.setTotalCashIn(session.getTotalCashIn().add(paidCash));

        // Decrement stock per line — permit negative per spec §3.1.3
        for (SaleLine line : builtLines) {
            stockOps.issueAllowNegative(
                    register.getWarehouseId(), line.getProductId(), line.getBaseQuantity(),
                    StockMovementType.SALE, "SALE", sale.getId(), sale.getNumber(),
                    null, userId);
        }

        return toDto(sale);
    }

    @Transactional
    public SyncSalesResponse syncSales(List<CreateSaleRequest> batch, UUID userId) {
        List<SyncSalesResponse.SyncResult> results = new ArrayList<>();
        for (CreateSaleRequest req : batch) {
            try {
                SaleDto dto = createSale(req, userId);
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
        return toDto(sales.findById(saleId)
                .orElseThrow(() -> NotFoundException.of("entity.sale", saleId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleDto> listSalesBySession(UUID sessionId, Pageable pageable) {
        return PageResponse.of(
                sales.findBySessionIdOrderByCompletedAtDesc(sessionId, pageable).map(this::toDto));
    }

    // ── Cash movements ───────────────────────────────────────────────────────

    @Transactional
    public CashMovementDto cashIn(UUID sessionId, BigDecimal amount, String reason, UUID userId) {
        CashSession session = sessions.lockById(sessionId)
                .orElseThrow(() -> NotFoundException.of("entity.cash_session", sessionId));
        requireOpenSession(session);
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("error.pos.invalid_amount", Map.of("amount", amount));
        }
        CashMovement m = CashMovement.builder()
                .sessionId(sessionId)
                .type(CashMovementType.PAY_IN)
                .amount(amount)
                .reason(reason)
                .userId(userId)
                .build();
        cashMovements.save(m);
        session.setTotalCashIn(session.getTotalCashIn().add(amount));
        return toCashMovementDto(m);
    }

    @Transactional
    public CashMovementDto cashOut(UUID sessionId, BigDecimal amount, String reason, UUID userId) {
        CashSession session = sessions.lockById(sessionId)
                .orElseThrow(() -> NotFoundException.of("entity.cash_session", sessionId));
        requireOpenSession(session);
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException("error.pos.invalid_amount", Map.of("amount", amount));
        }
        CashMovement m = CashMovement.builder()
                .sessionId(sessionId)
                .type(CashMovementType.PAY_OUT)
                .amount(amount.negate())
                .reason(reason)
                .userId(userId)
                .build();
        cashMovements.save(m);
        session.setTotalCashOut(session.getTotalCashOut().add(amount));
        return toCashMovementDto(m);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
                .map(l -> new SaleLineDto(l.getId(), l.getLineNumber(), l.getProductId(),
                        l.getUomId(), l.getQuantity(), l.getBaseQuantity(),
                        l.getUnitPrice(), l.getUnitDiscount(), l.getTaxRate(),
                        l.getSubtotal(), l.getTaxAmount(), l.getTotal(),
                        l.isTaxInclusive(), l.getSnapshotName(), l.getSnapshotSku()))
                .toList();
        return new SaleDto(s.getId(), s.getNumber(), s.getIdempotencyKey(),
                s.getRegisterId(), s.getSessionId(), s.getWarehouseId(),
                s.getCashierUserId(), s.getCustomerId(), s.getStatus().name(),
                s.getCurrency(), s.getSubtotal(), s.getTaxAmount(), s.getDiscountAmount(),
                s.getTotal(), s.getPaidCash(), s.getPaidCard(), s.getPaidMobile(),
                s.getPaidCredit(), s.getChangeDue(), s.getCompletedAt(), s.getNote(), lines);
    }

    private CashSessionDto toSessionDto(CashSession s) {
        return new CashSessionDto(s.getId(), s.getRegisterId(), s.getCashierUserId(),
                s.getStatus().name(), s.getOpenedAt(), s.getClosedAt(),
                s.getOpeningFloat(), s.getExpectedClosing(), s.getCountedClosing(),
                s.getDifference(), s.getTotalSales(), s.getTotalCashIn(),
                s.getTotalCashOut(), s.getNote());
    }

    private CashRegisterDto toRegisterDto(CashRegister r) {
        return new CashRegisterDto(r.getId(), r.getCode(), r.getName(),
                r.getWarehouseId(), r.getDefaultPriceTierId(), r.getReceiptWidthMm(), r.isActive());
    }

    private CashMovementDto toCashMovementDto(CashMovement m) {
        return new CashMovementDto(m.getId(), m.getSessionId(), m.getType().name(),
                m.getAmount(), m.getReason(), m.getOccurredAt());
    }

    // ── Request records ────────────────────────────────────────────────────────

    public record CreateRegisterRequest(
            String code,
            String name,
            UUID warehouseId,
            UUID defaultPriceTierId,
            Integer receiptWidthMm) {}
}
