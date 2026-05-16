package com.minierp.treasury.api;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TreasuryDto {

    private TreasuryDto() {}

    public record VaultResponse(UUID id, String name, String currency, BigDecimal balance) {}

    public record BankAccountResponse(
            UUID id, String name, String bankName, String accountNumber,
            String currency, BigDecimal balance, boolean active) {}

    public record VaultMovementResponse(
            UUID id, String type, BigDecimal amount,
            String referenceType, UUID referenceId,
            Instant occurredAt, UUID userId, String note) {}

    public record BankTransactionResponse(
            UUID id, UUID bankAccountId, String type, BigDecimal amount,
            UUID vaultMovementId, String reference, Instant occurredAt,
            UUID userId, String note) {}

    public record CreateBankAccountRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String bankName,
            @Size(max = 100) String accountNumber,
            @Size(min = 3, max = 3) String currency,
            BigDecimal openingBalance) {}

    public record UpdateBankAccountRequest(
            @Size(max = 200) String name,
            @Size(max = 200) String bankName,
            @Size(max = 100) String accountNumber,
            Boolean active) {}

    public record DepositRequest(
            @NotNull UUID bankAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 200) String reference,
            Instant occurredAt,
            @Size(max = 1000) String note) {}

    public record WithdrawalRequest(
            @NotNull UUID bankAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 200) String reference,
            Instant occurredAt,
            @Size(max = 1000) String note) {}

    public record AdjustVaultRequest(
            @NotNull BigDecimal amountSigned,
            @Size(max = 1000) String note) {}

    public record AdjustBankRequest(
            @NotNull UUID bankAccountId,
            @NotNull BigDecimal amountSigned,
            @Size(max = 1000) String note) {}
}
