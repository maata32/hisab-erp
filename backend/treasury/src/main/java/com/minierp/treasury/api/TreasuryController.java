package com.minierp.treasury.api;

import com.minierp.shared.security.CurrentUserHolder;
import com.minierp.shared.util.PageResponse;
import com.minierp.treasury.internal.TreasuryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/treasury")
@RequiredArgsConstructor
@Tag(name = "Treasury", description = "Central vault and bank accounts — paired movements")
public class TreasuryController {

    private final TreasuryService service;

    // ── Vault ────────────────────────────────────────────────────────────────

    @GetMapping("/vault")
    @PreAuthorize("hasAuthority('treasury:read')")
    public TreasuryDto.VaultResponse getVault() {
        return service.getOrCreateTenantVault();
    }

    @PostMapping("/vault/adjust")
    @PreAuthorize("hasAuthority('treasury:manage')")
    public TreasuryDto.VaultMovementResponse adjustVault(@Valid @RequestBody TreasuryDto.AdjustVaultRequest req) {
        return service.adjustVault(req.amountSigned(), req.note(), currentUserId());
    }

    @GetMapping("/vault/movements")
    @PreAuthorize("hasAuthority('treasury:read')")
    public PageResponse<TreasuryDto.VaultMovementResponse> listVaultMovements(
            @RequestParam(required = false) java.time.Instant from,
            @RequestParam(required = false) java.time.Instant to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.listVaultMovements(from, to, pageable));
    }

    // ── Bank accounts ────────────────────────────────────────────────────────

    @GetMapping("/bank-accounts")
    @PreAuthorize("hasAuthority('treasury:read')")
    public List<TreasuryDto.BankAccountResponse> listBankAccounts(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listBankAccounts(includeInactive);
    }

    @PostMapping("/bank-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('treasury:manage')")
    public TreasuryDto.BankAccountResponse createBankAccount(
            @Valid @RequestBody TreasuryDto.CreateBankAccountRequest req) {
        return service.createBankAccount(req);
    }

    @PatchMapping("/bank-accounts/{id}")
    @PreAuthorize("hasAuthority('treasury:manage')")
    public TreasuryDto.BankAccountResponse updateBankAccount(
            @PathVariable UUID id,
            @Valid @RequestBody TreasuryDto.UpdateBankAccountRequest req) {
        return service.updateBankAccount(id, req);
    }

    @PostMapping("/bank-accounts/{id}/adjust")
    @PreAuthorize("hasAuthority('treasury:manage')")
    public TreasuryDto.BankTransactionResponse adjustBankAccount(
            @PathVariable UUID id,
            @Valid @RequestBody TreasuryDto.AdjustBankRequest req) {
        if (!id.equals(req.bankAccountId())) {
            throw new IllegalArgumentException("path id and body bankAccountId must match");
        }
        return service.adjustBankAccount(id, req.amountSigned(), req.note(), currentUserId());
    }

    @GetMapping("/bank-accounts/{id}/transactions")
    @PreAuthorize("hasAuthority('treasury:read')")
    public PageResponse<TreasuryDto.BankTransactionResponse> listBankTransactions(
            @PathVariable UUID id,
            @RequestParam(required = false) java.time.Instant from,
            @RequestParam(required = false) java.time.Instant to,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(service.listBankTransactions(id, from, to, pageable));
    }

    // ── Transfers ────────────────────────────────────────────────────────────

    @PostMapping("/deposit")
    @PreAuthorize("hasAuthority('treasury:manage')")
    public TreasuryDto.BankTransactionResponse deposit(@Valid @RequestBody TreasuryDto.DepositRequest req) {
        return service.depositToBank(req, currentUserId());
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasAuthority('treasury:manage')")
    public TreasuryDto.BankTransactionResponse withdraw(@Valid @RequestBody TreasuryDto.WithdrawalRequest req) {
        return service.withdrawFromBank(req, currentUserId());
    }

    private static UUID currentUserId() {
        return CurrentUserHolder.tryGet().map(u -> u.userId()).orElse(null);
    }
}
