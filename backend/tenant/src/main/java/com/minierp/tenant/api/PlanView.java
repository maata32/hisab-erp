package com.minierp.tenant.api;

import java.math.BigDecimal;
import java.util.List;

/** Public view of a subscription plan, used by the self-service registration form. */
public record PlanView(
        String code,
        String name,
        BigDecimal monthlyPrice,
        BigDecimal annualPrice,
        Integer maxUsers,
        Integer maxProducts,
        Integer maxCashRegisters,
        Integer maxProductImages,
        List<String> enabledModules) {}
