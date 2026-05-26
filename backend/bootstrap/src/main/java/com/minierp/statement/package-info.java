/**
 * Cross-module statement aggregator. Lives under bootstrap because it pulls
 * read-only data from customer, sales, payment and document modules — none of
 * which depend on each other in this direction.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Statement",
        allowedDependencies = {
                "partner::customer-api",
                "sales::sales-api",
                "payment::payment-api",
                "document::document-api",
                "tenant::api",
                "shared"
        })
package com.minierp.statement;
