@org.springframework.modulith.ApplicationModule(
        displayName = "Sales",
        allowedDependencies = {"shared", "partner::customer-api", "catalog::api", "uom::api", "document::document-api", "tenant::api"}
)
package com.minierp.sales;
