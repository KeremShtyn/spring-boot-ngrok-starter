package com.kermel.ngrok.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NgrokHealthIndicatorTest {

    private NgrokTunnelRegistry tunnelRegistry;
    private NgrokHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        tunnelRegistry = new NgrokTunnelRegistry();
        healthIndicator = new NgrokHealthIndicator(tunnelRegistry);
    }

    @Test
    void downWhenNoTunnels() {
        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("status");
        assertThat(health.getDetails().get("status")).isEqualTo("No active tunnels");
    }

    @Test
    void upWithSingleTunnel() {
        tunnelRegistry.register(new NgrokTunnel("default", "https://abc.ngrok-free.app", 8080, "http"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("tunnelCount");
        assertThat(health.getDetails().get("tunnelCount")).isEqualTo(1);
        assertThat(health.getDetails()).containsKey("uptime");
        assertThat(health.getDetails()).containsKey("tunnel:default");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tunnelDetailsIncluded() {
        tunnelRegistry.register(new NgrokTunnel("default", "https://abc.ngrok-free.app", 8080, "http"));

        Health health = healthIndicator.health();

        Map<String, Object> tunnelDetail = (Map<String, Object>) health.getDetails().get("tunnel:default");
        assertThat(tunnelDetail).isNotNull();
        assertThat(tunnelDetail.get("publicUrl")).isEqualTo("https://abc.ngrok-free.app");
        assertThat(tunnelDetail.get("localPort")).isEqualTo(8080);
        assertThat(tunnelDetail.get("protocol")).isEqualTo("http");
        assertThat(tunnelDetail).containsKey("createdAt");
        assertThat(tunnelDetail).containsKey("tunnelUptime");
    }

    @Test
    void upWithMultipleTunnels() {
        tunnelRegistry.register(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
        tunnelRegistry.register(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("tunnelCount")).isEqualTo(2);
        assertThat(health.getDetails()).containsKey("tunnel:api");
        assertThat(health.getDetails()).containsKey("tunnel:db");
    }
}
