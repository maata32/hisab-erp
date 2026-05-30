package com.minierp.payment.api;

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
            @NotNull UUID partyId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            String currency,
            LocalDate paymentDate,
            @NotBlank String method,
            String reference,
            String bankAccount,
            String notes,
            List<AllocationRequest> allocations
    ) {}

    public record AutoAllocateRequest(
            @NotNull UUID customerId,
            @NotNull UUID paymentId,
            String strategy
    ) {}

    public record AllocateRequest(
            List<AllocationRequest> allocations
    ) {}

    public record RefundRequest(
            LocalDate paymentDate,
            @NotBlank String method,
            String reference,
            String reason
    ) {}

    /**
     * One row in the impact preview shown before confirming a refund. Tells the
     * UI: which allocation will be undone, on which document, for how much, and
     * what the document will look like once the reversal lands.
     */
    public record RefundImpactRow(
            String targetType,
            UUID targetId,
            String targetLabel,
            BigDecimal amount,
            String afterStatus
    ) {}

    public record RefundPreviewResponse(
            UUID paymentId,
            String paymentNumber,
            BigDecimal amount,
            String currency,
            UUID partyId,
            String partyName,
            BigDecimal revokableCreditAmount,
            List<RefundImpactRow> impacts
    ) {}
}
