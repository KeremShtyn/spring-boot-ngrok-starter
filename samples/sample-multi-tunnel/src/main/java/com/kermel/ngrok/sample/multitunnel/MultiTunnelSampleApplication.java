package com.kermel.ngrok.sample.multitunnel;

import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.event.NgrokReadyEvent;
import com.kermel.ngrok.event.NgrokTunnelEstablishedEvent;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sample application demonstrating multi-tunnel configuration.
 *
 * <p>This example exposes the same Spring Boot app through multiple ngrok tunnels,
 * each with different configurations: a public API tunnel, a protected admin tunnel,
 * and a TCP tunnel for database access.
 *
 * <p>Run with: {@code NGROK_AUTHTOKEN=xxx mvn spring-boot:run -Dspring.profiles.active=dev}
 */
@SpringBootApplication
@RestController
public class MultiTunnelSampleApplication {

    private final NgrokTunnelRegistry tunnelRegistry;

    public MultiTunnelSampleApplication(NgrokTunnelRegistry tunnelRegistry) {
        this.tunnelRegistry = tunnelRegistry;
    }

    public static void main(String[] args) {
        SpringApplication.run(MultiTunnelSampleApplication.class, args);
    }

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("message", "Multi-Tunnel Sample Application");
    }

    @GetMapping("/api/status")
    public Map<String, Object> apiStatus() {
        return Map.of(
                "status", "ok",
                "activeTunnels", tunnelRegistry.size(),
                "tunnels", tunnelRegistry.getAllTunnels().stream()
                        .collect(Collectors.toMap(NgrokTunnel::name, NgrokTunnel::publicUrl))
        );
    }

    @GetMapping("/admin/dashboard")
    public Map<String, String> adminDashboard() {
        return Map.of("panel", "admin", "access", "protected");
    }

    /**
     * Fires for each individual tunnel as it comes online.
     */
    @EventListener
    public void onTunnelEstablished(NgrokTunnelEstablishedEvent event) {
        NgrokTunnel tunnel = event.getTunnel();
        System.out.printf("[%s] Tunnel established: %s (%s)%n",
                tunnel.name(), tunnel.publicUrl(), tunnel.protocol());
    }

    /**
     * Fires once when ALL tunnels are ready.
     */
    @EventListener
    public void onAllTunnelsReady(NgrokReadyEvent event) {
        System.out.println("\nAll ngrok tunnels are ready!");
        System.out.println("Total tunnels: " + event.getTunnels().size());
        event.getTunnels().forEach(t ->
                System.out.printf("  %-12s %s -> localhost:%d%n",
                        t.name(), t.publicUrl(), t.localPort()));
    }
}
