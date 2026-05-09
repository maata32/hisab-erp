@org.springframework.modulith.ApplicationModule(
        displayName = "Payment",
        allowedDependencies = {"shared", "customer::customer-api", "sales::sales-api", "document::document-api"}
)
package com.minierp.payment;
