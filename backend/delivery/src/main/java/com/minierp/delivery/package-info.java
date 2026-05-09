@org.springframework.modulith.ApplicationModule(
        displayName = "Delivery",
        allowedDependencies = {"shared", "customer::customer-api", "sales::sales-api", "document::document-api"}
)
package com.minierp.delivery;
