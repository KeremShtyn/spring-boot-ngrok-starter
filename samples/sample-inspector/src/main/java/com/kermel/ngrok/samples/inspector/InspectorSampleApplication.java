package com.kermel.ngrok.samples.inspector;

import com.kermel.ngrok.inspector.CapturedRequest;
import com.kermel.ngrok.inspector.CapturedResponse;
import com.kermel.ngrok.inspector.NgrokInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sample application demonstrating ngrok request inspection.
 *
 * <p>Features demonstrated:
 * <ul>
 *   <li>Querying recent captured requests</li>
 *   <li>Filtering requests by path and method</li>
 *   <li>Viewing error requests</li>
 *   <li>Replaying captured requests</li>
 *   <li>Actuator endpoint at /actuator/ngrokrequests</li>
 * </ul>
 *
 * <p>Try it:
 * <pre>
 * # Make some requests through ngrok, then:
 * curl localhost:8082/inspector/recent
 * curl localhost:8082/inspector/errors
 * curl localhost:8082/inspector/search?path=/api/.*
 * curl -X POST localhost:8082/inspector/replay/req_abc123
 * curl localhost:8082/actuator/ngrokrequests
 * </pre>
 */
@SpringBootApplication
@RestController
public class InspectorSampleApplication {

    private static final Logger log = LoggerFactory.getLogger(InspectorSampleApplication.class);

    private final NgrokInspector inspector;

    public InspectorSampleApplication(NgrokInspector inspector) {
        this.inspector = inspector;
    }

    public static void main(String[] args) {
        SpringApplication.run(InspectorSampleApplication.class, args);
    }

    // --- Sample endpoints that generate traffic to inspect ---

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "message", "ngrok Request Inspector Sample",
                "timestamp", Instant.now().toString(),
                "endpoints", List.of(
                        "GET /api/users - sample API",
                        "POST /api/orders - sample API",
                        "GET /inspector/recent - view recent requests",
                        "GET /inspector/errors - view error requests",
                        "GET /inspector/search?path=... - search by path",
                        "POST /inspector/replay/{id} - replay a request",
                        "GET /actuator/ngrokrequests - actuator endpoint"
                )
        );
    }

    @GetMapping("/api/users")
    public List<Map<String, Object>> getUsers() {
        return List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
        );
    }

    @PostMapping("/api/orders")
    public Map<String, Object> createOrder(@RequestBody(required = false) Map<String, Object> order) {
        return Map.of(
                "orderId", "ord_" + System.currentTimeMillis(),
                "status", "created",
                "received", order != null ? order : Map.of()
        );
    }

    @GetMapping("/api/error")
    public ResponseEntity<Map<String, String>> triggerError() {
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "Simulated server error for testing"));
    }

    // --- Inspector endpoints ---

    @GetMapping("/inspector/recent")
    public List<CapturedRequest> recentRequests(
            @RequestParam(defaultValue = "20") int limit) {
        return inspector.getRecentRequests(limit);
    }

    @GetMapping("/inspector/errors")
    public List<CapturedRequest> errorRequests() {
        return inspector.getErrorRequests();
    }

    @GetMapping("/inspector/search")
    public List<CapturedRequest> searchByPath(@RequestParam String path) {
        return inspector.getRequestsByPath(path);
    }

    @GetMapping("/inspector/status")
    public Map<String, Object> inspectorStatus() {
        return Map.of(
                "available", inspector.isAvailable(),
                "recentCount", inspector.getRecentRequests().size()
        );
    }

    @PostMapping("/inspector/replay/{id}")
    public Map<String, Object> replayRequest(@PathVariable String id) {
        Optional<CapturedResponse> response = inspector.replay(id);
        if (response.isPresent()) {
            CapturedResponse r = response.get();
            return Map.of(
                    "replayed", true,
                    "statusCode", r.statusCode(),
                    "durationMs", r.durationMs()
            );
        }
        return Map.of("replayed", false, "error", "Replay failed or request not found");
    }

    @DeleteMapping("/inspector/clear")
    public Map<String, String> clearRequests() {
        inspector.clearRequests();
        return Map.of("status", "cleared");
    }
}
