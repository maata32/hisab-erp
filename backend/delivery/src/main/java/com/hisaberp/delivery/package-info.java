@org.springframework.modulith.ApplicationModule(
        displayName = "Delivery",
        allowedDependencies = {"shared", "partner::customer-api", "sales::sales-api", "inventory::api", "lotexpiry::api", "document::document-api", "tenant::api"}
)
package com.hisaberp.delivery;
