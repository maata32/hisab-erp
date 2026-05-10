@org.springframework.modulith.ApplicationModule(
        displayName = "Sales",
        allowedDependencies = {"shared", "customer::customer-api", "catalog::api", "uom::api", "document::document-api", "tenant::api"}
)
package com.minierp.sales;
