package com.hisaberp.payment.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class PaymentDto {

    public record AllocationDto(
            UUID id,
            String targetType,
            UUID targetId,
            BigDecimal allocatedAmount,
            String notes
    ) {}

    public record PaymentResponse(
            UUID id,
            String number,
            String type,
            UUID partyId,
            String partyName,
            BigDecimal amount,
            String currency,
            LocalDate paymentDate,
            String method,
            String reference,
            String status,
            String notes,
            List<AllocationDto> allocations,
            Instant createdAt
    ) {}

    public record AllocationRequest(
            @NotNull String targetType,
            @NotNull UUID targetId,
            @NotNull @DecimalMin("0.01") BigDecimal allocatedAmount,
            String notes
    ) {}

    public record CreatePaymentRequest(
            @NotNull String type,
            // Nullable for expense payments (all-EXPENSE allocation set); required
            // for partner payments — validated in PaymentService.create().
            UUID partyId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String currency,
            LocalDate paymentDate,
            @NotBlank String method,
            String reference,
            String bankAccount,
            UUID bankAccountId,
            String notes,
            List<AllocationRequest> allocations
    ) {
        /** Back-compat constructor (no treasury bank account) — keeps existing
         *  callers compiling; the canonical 11-arg form is used by JSON binding. */
        public CreatePaymentRequest(String type, UUID partyId, BigDecimal amount, String currency,
                                    LocalDate paymentDate, String method, String reference,
                                    String bankAccount, String notes, List<AllocationRequest> allocations) {
            this(type, partyId, amount, currency, paymentDate, method, reference,
                    bankAccount, null, notes, allocations);
        }
    }

    public record AutoAllocateRequest(
            @NotNull UUID customerId,
            @NotNull UUID paymentId,
            String strategy
    ) {}

    public record AllocateRequest(
            List<AllocationRequest> allocations
    ) {}

}
