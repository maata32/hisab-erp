@org.springframework.modulith.ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {"shared", "tenant", "tenant::api", "tenant::events"})
package com.minierp.identity;
