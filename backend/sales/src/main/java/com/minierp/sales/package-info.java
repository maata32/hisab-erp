@org.springframework.modulith.ApplicationModule(
        displayName = "Sales",
        allowedDependencies = {"shared", "customer::customer-api", "catalog::catalog-api", "uom::uom-api", "document::document-api", "tenant::tenant-api"}
)
package com.minierp.sales;
