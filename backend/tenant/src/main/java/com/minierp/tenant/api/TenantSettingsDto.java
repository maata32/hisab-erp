package com.minierp.tenant.api;

import java.util.Map;

public record TenantSettingsDto(
        Map<String, Object> posSettings,
        Map<String, Object> invoiceSettings,
        Map<String, Object> paymentSettings,
        Map<String, Object> notificationSettings,
        Map<String, Object> pricingSettings,
        Map<String, Object> deliverySettings) {}
