@org.springframework.modulith.ApplicationModule(
        displayName = "Sales",
        allowedDependencies = {"shared", "partner::customer-api", "catalog::api", "uom::api", "inventory::api", "document::document-api", "tenant::api"}
)
package com.hisaberp.sales;
