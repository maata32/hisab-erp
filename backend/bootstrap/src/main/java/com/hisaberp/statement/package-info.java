/**
 * Cross-module statement aggregator. Lives under bootstrap because it pulls
 * read-only data from customer, sales, purchase, payment and document modules —
 * none of which depend on each other in this direction. The statement is a
 * unified partner ledger: sales (AR) and purchase (AP) movements merged into a
 * single chronological timeline with a net running balance.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Statement",
        allowedDependencies = {
                "partner::customer-api",
                "sales::sales-api",
                "purchase::purchase-api",
                "payment::payment-api",
                "document::document-api",
                "tenant::api",
                "shared"
        })
package com.hisaberp.statement;
