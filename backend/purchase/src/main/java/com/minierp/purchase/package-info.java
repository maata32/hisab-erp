@org.springframework.modulith.ApplicationModule(
        displayName = "Purchase",
        allowedDependencies = {
                "shared",
                "partner::customer-api",
                "catalog::api",
                "uom::api",
                "inventory::api",
                "lotexpiry::api",
                "sales::sales-api",
                "document::document-api",
                "tenant::api"
        }
)
package com.minierp.purchase;
