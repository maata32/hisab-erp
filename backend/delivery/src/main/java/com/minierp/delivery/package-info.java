@org.springframework.modulith.ApplicationModule(
        displayName = "Delivery",
        allowedDependencies = {"shared", "partner::customer-api", "sales::sales-api", "inventory::api", "document::document-api", "tenant::api"}
)
package com.minierp.delivery;
