package com.minierp;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Liveness ping (Actuator covers /actuator/health)")
public class HealthController {

    @GetMapping
    @Operation(summary = "Public health probe — returns 200 if the app is up")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now(),
                "service", "mini-erp");
    }
}
