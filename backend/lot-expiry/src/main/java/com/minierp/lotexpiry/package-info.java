@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {"inventory::api", "catalog::api", "uom", "shared", "document::document-api"}
)
package com.minierp.lotexpiry;
