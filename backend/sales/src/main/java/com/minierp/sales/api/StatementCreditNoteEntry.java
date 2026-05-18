package com.minierp.sales.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record StatementCreditNoteEntry(
        UUID id,
        String number,
        LocalDate issueDate,
        BigDecimal amount,
        String reason,
        String status
) {}
