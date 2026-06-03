package com.minierp.sales.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StatementCreditNoteEntry(
        UUID id,
        UUID invoiceId,
        String number,
        LocalDate issueDate,
        BigDecimal amount,
        String reason,
        String status,
        Instant createdAt
) {}
