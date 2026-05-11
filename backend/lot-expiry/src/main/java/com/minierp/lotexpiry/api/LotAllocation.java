package com.minierp.lotexpiry.api;

import java.math.BigDecimal;
import java.util.UUID;

/** A single lot's allocation in a FEFO pick. */
public record LotAllocation(UUID lotId, BigDecimal quantity) {}
