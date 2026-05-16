@org.springframework.modulith.ApplicationModule(
        displayName = "POS",
        allowedDependencies = {"shared", "catalog", "catalog::api", "uom", "uom::api", "pricing", "pricing::api", "inventory", "inventory::api", "notifications", "tenant", "treasury", "treasury::api"})
package com.minierp.pos;
