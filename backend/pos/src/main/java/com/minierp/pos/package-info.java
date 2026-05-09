@org.springframework.modulith.ApplicationModule(
        displayName = "POS",
        allowedDependencies = {"shared", "catalog", "catalog::api", "uom", "uom::api", "pricing", "pricing::api", "inventory", "inventory::api", "notifications", "tenant"})
package com.minierp.pos;
