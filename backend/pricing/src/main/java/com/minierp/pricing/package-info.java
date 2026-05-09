@org.springframework.modulith.ApplicationModule(
        displayName = "Pricing",
        allowedDependencies = {"shared", "catalog::api", "uom", "tenant", "tenant::events"})
package com.minierp.pricing;
