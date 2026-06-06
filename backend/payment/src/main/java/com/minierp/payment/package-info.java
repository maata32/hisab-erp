@org.springframework.modulith.ApplicationModule(
        displayName = "Payment",
        allowedDependencies = {"shared", "partner::customer-api", "sales::sales-api", "purchase::purchase-api", "document::document-api", "tenant::api", "expense::api", "treasury::api"}
)
package com.minierp.payment;
