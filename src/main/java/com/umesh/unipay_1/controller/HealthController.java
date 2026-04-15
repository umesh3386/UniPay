package com.umesh.unipay_1.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health Check", description = "Public health endpoint to keep hosting containers from sleeping")
public class HealthController {

    @GetMapping
    @Operation(summary = "Ping Server", description = "Returns dynamic status and timestamp. Intended for UptimeRobot.")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of("status", "UP", "ts", Instant.now()));
    }
}
