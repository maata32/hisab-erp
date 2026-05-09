@org.springframework.modulith.ApplicationModule(
        displayName = "Inventory",
        allowedDependencies = {"shared", "catalog", "catalog::api", "uom", "uom::api", "tenant", "tenant::events"})
package com.minierp.inventory;
