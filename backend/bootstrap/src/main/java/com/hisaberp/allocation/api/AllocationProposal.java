package com.hisaberp.allocation.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Output of {@link AllocationEngine#propose(UUID, String, UUID, BigDecimal)}:
 * a FIFO-ordered list of allocation lines that would settle the source item
 * against the open items of the opposite sign, plus the surplus (if the
 * source amount exceeds the sum of opposite-side open balances).
 */
public record AllocationProposal(
        List<AllocationLine> lines,
        BigDecimal surplus) {}
