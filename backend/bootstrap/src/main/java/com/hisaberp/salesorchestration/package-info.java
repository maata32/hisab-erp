/**
 * Cross-module sales orchestrators. Lives under bootstrap because some flows
 * (e.g. "create invoice and ship immediately") need to compose a write call on
 * sales with a write call on delivery — neither module can call the other
 * directly without breaking the dependency direction.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Sales Orchestration",
        allowedDependencies = {
                "sales::sales-api",
                "delivery::delivery-api",
                "identity::security",
                "shared"
        })
package com.hisaberp.salesorchestration;
