package com.kermel.ngrok.sample.policy;

import com.kermel.ngrok.event.NgrokReadyEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sample application demonstrating the Traffic Policy DSL.
 *
 * <p>Run with: {@code NGROK_AUTHTOKEN=xxx mvn spring-boot:run -Dspring.profiles.active=dev}
 */
@SpringBootApplication
@RestController
public class TrafficPolicySampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrafficPolicySampleApplication.class, args);
    }

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("message", "Traffic Policy DSL Sample");
    }

    @GetMapping("/api/data")
    public Map<String, Object> apiData() {
        return Map.of("items", 42, "status", "ok");
    }

    @GetMapping("/api/users/{id}")
    public Map<String, Object> getUser(@PathVariable String id) {
        return Map.of("id", id, "name", "Sample User");
    }

    @GetMapping("/admin/dashboard")
    public Map<String, String> adminDashboard() {
        return Map.of("panel", "admin", "status", "active");
    }

    @EventListener
    public void onNgrokReady(NgrokReadyEvent event) {
        event.getTunnels().forEach(tunnel ->
                System.out.println("ngrok tunnel ready: " + tunnel.publicUrl()));
    }
}
