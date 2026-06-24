package com.minierp.treasury.internal;

import com.minierp.shared.error.BusinessException;
import com.minierp.shared.error.NotFoundException;
import com.minierp.shared.persistence.TenantGuard;
import com.minierp.shared.tenant.TenantContext;
import com.minierp.treasury.api.TreasuryDto;
import com.minierp.treasury.api.TreasuryOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single entrypoint for the central vault + bank accounts. All money movements
 * are appended-only ledger lines paired with an atomic balance update under
 * pessimistic lock.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TreasuryService implements TreasuryOperations {

    private final VaultRepository vaults;
    private final VaultMovementRepository vaultMovements;
    private final BankAccountRepository bankAccounts;
    private final BankTransactionRepository bankTransactions;

    // ── Vault ────────────────────────────────────────────────────────────────

    @Transactional
    public TreasuryDto.VaultResponse getOrCreateTenantVault() {
        Vault v = vaults.findByTenant(TenantContext.require())
                .orElseGet(() -> vaults.save(Vault.builder().build()));
        return toVaultDto(v);
    }

    @Transactional
    public TreasuryDto.VaultMovementResponse adjustVault(BigDecimal amountSigned, String note, UUID userId) {
        if (amountSigned == null || amountSigned.signum() == 0) {
            throw new BusinessException("error.treasury.invalid_adjustment");
        }
        Vault v = lockVault();
        v.setBalance(v.getBalance().add(amountSigned));
        VaultMovement m = persistVaultMovement(v.getId(), VaultMovementType.ADJUSTMENT,
                amountSigned, null, null, userId, note);
        return toMovementDto(m);
    }

    // ── Bank accounts ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TreasuryDto.BankAccountResponse> listBankAccounts(boolean includeInactive) {
        var list = includeInactive
                ? bankAccounts.findAllByOrderByActiveDescNameAsc()
                : bankAccounts.findByActiveTrueOrderByNameAsc();
        return list.stream().map(TreasuryService::toBankDto).toList();
    }

    @Transactional
    public TreasuryDto.BankAccountResponse createBankAccount(TreasuryDto.CreateBankAccountRequest req) {
        BigDecimal opening = req.openingBalance() == null ? BigDecimal.ZERO : req.openingBalance();
        BankAccount b = BankAccount.builder()
                .name(req.name())
                .bankName(req.bankName())
                .accountNumber(req.accountNumber())
                .currency(req.currency() == null ? "MRU" : req.currency())
                .balance(opening)
                .active(true)
                .build();
        bankAccounts.save(b);
        return toBankDto(b);
    }

    @Transactional
    public TreasuryDto.BankAccountResponse updateBankAccount(UUID id, TreasuryDto.UpdateBankAccountRequest req) {
        BankAccount b = loadBankAccountInTenant(id);
        if (req.name() != null) b.setName(req.name());
        if (req.bankName() != null) b.setBankName(req.bankName());
        if (req.accountNumber() != null) b.setAccountNumber(req.accountNumber());
        if (req.active() != null) b.setActive(req.active());
        return toBankDto(b);
    }

    @Transactional
    public TreasuryDto.BankTransactionResponse adjustBankAccount(UUID bankAccountId,
                                                                 BigDecimal amountSigned,
                                                                 String note, UUID userId) {
        if (amountSigned == null || amountSigned.signum() == 0) {
            throw new BusinessException("error.treasury.invalid_adjustment");
        }
        BankAccount b = lockBankAccountInTenant(bankAccountId);
        b.setBalance(b.getBalance().add(amountSigned));
        BankTransaction t = persistBankTransaction(b.getId(), BankTransactionType.ADJUSTMENT,
                amountSigned, null, null, Instant.now(), userId, note);
        return toBankTxnDto(t);
    }

    // ── Transfers vault ↔ bank ──────────────────────────────────────────────

    @Transactional
    public TreasuryDto.BankTransactionResponse depositToBank(TreasuryDto.DepositRequest req, UUID userId) {
        requirePositive(req.amount());
        Vault v = lockVault();
        BankAccount b = lockBankAccountInTenant(req.bankAccountId());

        Instant when = req.occurredAt() != null ? req.occurredAt() : Instant.now();

        // Vault −amount
        v.setBalance(v.getBalance().subtract(req.amount()));
        VaultMovement vm = persistVaultMovement(v.getId(), VaultMovementType.TO_BANK,
                req.amount().negate(), "BANK_TRANSACTION", null, userId, req.note());

        // Bank +amount
        b.setBalance(b.getBalance().add(req.amount()));
        BankTransaction bt = persistBankTransaction(b.getId(), BankTransactionType.DEPOSIT_FROM_VAULT,
                req.amount(), vm.getId(), req.reference(), when, userId, req.note());

        // Backfill referenceId on vault movement so both sides link.
        vm.setReferenceId(bt.getId());
        vm.setOccurredAt(when);
        return toBankTxnDto(bt);
    }

    @Transactional
    public TreasuryDto.BankTransactionResponse withdrawFromBank(TreasuryDto.WithdrawalRequest req, UUID userId) {
        requirePositive(req.amount());
        Vault v = lockVault();
        BankAccount b = lockBankAccountInTenant(req.bankAccountId());

        Instant when = req.occurredAt() != null ? req.occurredAt() : Instant.now();

        // Bank −amount
        b.setBalance(b.getBalance().subtract(req.amount()));
        BankTransaction bt = persistBankTransaction(b.getId(), BankTransactionType.WITHDRAWAL_TO_VAULT,
                req.amount().negate(), null, req.reference(), when, userId, req.note());

        // Vault +amount
        v.setBalance(v.getBalance().add(req.amount()));
        VaultMovement vm = persistVaultMovement(v.getId(), VaultMovementType.FROM_BANK,
                req.amount(), "BANK_TRANSACTION", bt.getId(), userId, req.note());
        vm.setOccurredAt(when);

        bt.setVaultMovementId(vm.getId());
        return toBankTxnDto(bt);
    }

    // ── POS hooks (TreasuryOperations) ──────────────────────────────────────

    @Override
    @Transactional
    public void depositFromPosSession(UUID sessionId, BigDecimal amount, UUID userId) {
        if (amount == null || amount.signum() <= 0) return; // no-op
        Vault v = lockVault();
        v.setBalance(v.getBalance().add(amount));
        persistVaultMovement(v.getId(), VaultMovementType.FROM_POS_SESSION,
                amount, "POS_SESSION", sessionId, userId, null);
    }

    // ── Payment hooks (TreasuryOperations) ──────────────────────────────────

    @Override
    @Transactional
    public void recordVaultMovement(BigDecimal amountSigned, String referenceType, UUID referenceId,
                                    UUID userId, String note) {
        if (amountSigned == null || amountSigned.signum() == 0) return; // no-op
        Vault v = lockVault();
        v.setBalance(v.getBalance().add(amountSigned));
        persistVaultMovement(v.getId(), VaultMovementType.PAYMENT,
                amountSigned, referenceType, referenceId, userId, note);
    }

    @Override
    @Transactional
    public void recordBankMovement(UUID bankAccountId, BigDecimal amountSigned, String referenceType,
                                   UUID referenceId, UUID userId, String note) {
        if (amountSigned == null || amountSigned.signum() == 0) return; // no-op
        BankAccount b = bankAccounts.lockById(bankAccountId)
                .orElseThrow(() -> NotFoundException.of("entity.bank_account", bankAccountId));
        b.setBalance(b.getBalance().add(amountSigned));
        persistBankTransaction(b.getId(), BankTransactionType.PAYMENT,
                amountSigned, null, note, Instant.now(), userId, note);
    }

    // ── History ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TreasuryDto.VaultMovementResponse> listVaultMovements(Instant from, Instant to, Pageable pageable) {
        Vault v = vaults.findByTenant(TenantContext.require())
                .orElseGet(() -> vaults.save(Vault.builder().build()));
        Page<VaultMovement> page = (from != null && to != null)
                ? vaultMovements.findByVaultIdAndOccurredAtBetweenOrderByOccurredAtDesc(v.getId(), from, to, pageable)
                : vaultMovements.findByVaultIdOrderByOccurredAtDesc(v.getId(), pageable);
        return page.map(TreasuryService::toMovementDto);
    }

    @Transactional(readOnly = true)
    public Page<TreasuryDto.BankTransactionResponse> listBankTransactions(UUID bankAccountId, Instant from, Instant to, Pageable pageable) {
        if (bankAccounts.findById(bankAccountId)
                .filter(b -> b.getTenantId().equals(TenantContext.require()))
                .isEmpty()) {
            throw NotFoundException.of("entity.bank_account", bankAccountId);
        }
        Page<BankTransaction> page = (from != null && to != null)
                ? bankTransactions.findByBankAccountIdAndOccurredAtBetweenOrderByOccurredAtDesc(bankAccountId, from, to, pageable)
                : bankTransactions.findByBankAccountIdOrderByOccurredAtDesc(bankAccountId, pageable);
        return page.map(TreasuryService::toBankTxnDto);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    // Tenant-guarded by-id loads: findById/lockById bypass the Hibernate tenant filter.
    private BankAccount loadBankAccountInTenant(UUID id) {
        return TenantGuard.requireSameTenant(bankAccounts.findById(id),
                () -> NotFoundException.of("entity.bank_account", id));
    }

    private BankAccount lockBankAccountInTenant(UUID id) {
        return TenantGuard.requireSameTenant(bankAccounts.lockById(id),
                () -> NotFoundException.of("entity.bank_account", id));
    }

    private Vault lockVault() {
        UUID tenantId = TenantContext.require();
        return vaults.lockByTenant(tenantId)
                .orElseGet(() -> vaults.save(Vault.builder().build()));
    }

    private VaultMovement persistVaultMovement(UUID vaultId, VaultMovementType type,
                                               BigDecimal amount, String refType, UUID refId,
                                               UUID userId, String note) {
        VaultMovement m = VaultMovement.builder()
                .vaultId(vaultId)
                .type(type)
                .amount(amount)
                .referenceType(refType)
                .referenceId(refId)
                .occurredAt(Instant.now())
                .userId(userId)
                .note(note)
                .build();
        vaultMovements.save(m);
        return m;
    }

    private BankTransaction persistBankTransaction(UUID bankAccountId, BankTransactionType type,
                                                   BigDecimal amount, UUID vaultMovementId,
                                                   String reference, Instant when, UUID userId, String note) {
        BankTransaction t = BankTransaction.builder()
                .bankAccountId(bankAccountId)
                .type(type)
                .amount(amount)
                .vaultMovementId(vaultMovementId)
                .reference(reference)
                .occurredAt(when)
                .userId(userId)
                .note(note)
                .build();
        bankTransactions.save(t);
        return t;
    }

    private static void requirePositive(BigDecimal v) {
        if (v == null || v.signum() <= 0) {
            throw new BusinessException("error.treasury.non_positive_amount", Map.of("value", v));
        }
    }

    private static TreasuryDto.VaultResponse toVaultDto(Vault v) {
        return new TreasuryDto.VaultResponse(v.getId(), v.getName(), v.getCurrency(), v.getBalance());
    }

    private static TreasuryDto.BankAccountResponse toBankDto(BankAccount b) {
        return new TreasuryDto.BankAccountResponse(b.getId(), b.getName(), b.getBankName(),
                b.getAccountNumber(), b.getCurrency(), b.getBalance(), b.isActive());
    }

    private static TreasuryDto.VaultMovementResponse toMovementDto(VaultMovement m) {
        return new TreasuryDto.VaultMovementResponse(m.getId(), m.getType().name(), m.getAmount(),
                m.getReferenceType(), m.getReferenceId(), m.getOccurredAt(), m.getUserId(), m.getNote());
    }

    private static TreasuryDto.BankTransactionResponse toBankTxnDto(BankTransaction t) {
        return new TreasuryDto.BankTransactionResponse(t.getId(), t.getBankAccountId(),
                t.getType().name(), t.getAmount(), t.getVaultMovementId(),
                t.getReference(), t.getOccurredAt(), t.getUserId(), t.getNote());
    }
}
