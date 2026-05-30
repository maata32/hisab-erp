package com.minierp.allocation.api;

import java.math.BigDecimal;

/**
 * One leg of an {@link AllocationProposal}: how much of a given negative-side
 * item the proposed positive-side item will cover.
 */
public record AllocationLine(
        OpenItem positive,
        OpenItem negative,
        BigDecimal amount) {}
