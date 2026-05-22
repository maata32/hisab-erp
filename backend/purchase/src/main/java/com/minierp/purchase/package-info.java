@org.springframework.modulith.ApplicationModule(
        displayName = "Purchase",
        allowedDependencies = {
                "shared",
                "customer::customer-api",
                "catalog::api",
                "uom::api",
                "inventory::api",
                "lot-expiry::api",
                "sales::sales-api",
                "document::document-api",
                "tenant::api"
        }
)
package com.minierp.purchase;
