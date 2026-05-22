@org.springframework.modulith.ApplicationModule(
        displayName = "Payment",
        allowedDependencies = {"shared", "customer::customer-api", "sales::sales-api", "purchase::purchase-api", "document::document-api"}
)
package com.minierp.payment;
