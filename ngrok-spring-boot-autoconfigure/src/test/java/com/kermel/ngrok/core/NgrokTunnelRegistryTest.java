package com.kermel.ngrok.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NgrokTunnelRegistryTest {

    private NgrokTunnelRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NgrokTunnelRegistry();
    }

    @Test
    void startsEmpty() {
        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.size()).isZero();
        assertThat(registry.getAllTunnels()).isEmpty();
        assertThat(registry.getDefaultTunnel()).isNull();
        assertThat(registry.getPublicUrl()).isNull();
    }

    @Test
    void registerAndRetrieveTunnel() {
        NgrokTunnel tunnel = new NgrokTunnel("default", "https://abc123.ngrok-free.app", 8080, "http");
        registry.register(tunnel);

        assertThat(registry.isEmpty()).isFalse();
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.getTunnel("default")).isPresent();
        assertThat(registry.getTunnel("default").get().publicUrl()).isEqualTo("https://abc123.ngrok-free.app");
    }

    @Test
    void getDefaultTunnelByName() {
        NgrokTunnel tunnel = new NgrokTunnel("default", "https://abc123.ngrok-free.app", 8080, "http");
        registry.register(tunnel);

        assertThat(registry.getDefaultTunnel()).isNotNull();
        assertThat(registry.getDefaultTunnel().name()).isEqualTo("default");
    }

    @Test
    void getDefaultTunnelFallsBackToFirst() {
        NgrokTunnel tunnel = new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http");
        registry.register(tunnel);

        assertThat(registry.getDefaultTunnel()).isNotNull();
        assertThat(registry.getDefaultTunnel().name()).isEqualTo("api");
    }

    @Test
    void getPublicUrl() {
        NgrokTunnel tunnel = new NgrokTunnel("default", "https://abc123.ngrok-free.app", 8080, "http");
        registry.register(tunnel);

        assertThat(registry.getPublicUrl()).isEqualTo("https://abc123.ngrok-free.app");
    }

    @Test
    void getPublicUrlByName() {
        NgrokTunnel api = new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http");
        NgrokTunnel db = new NgrokTunnel("database", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp");
        registry.register(api);
        registry.register(db);

        assertThat(registry.getPublicUrl("api")).isEqualTo("https://api.ngrok-free.app");
        assertThat(registry.getPublicUrl("database")).isEqualTo("tcp://0.tcp.ngrok.io:12345");
        assertThat(registry.getPublicUrl("nonexistent")).isNull();
    }

    @Test
    void deregisterTunnel() {
        NgrokTunnel tunnel = new NgrokTunnel("default", "https://abc123.ngrok-free.app", 8080, "http");
        registry.register(tunnel);
        assertThat(registry.size()).isEqualTo(1);

        registry.deregister("default");
        assertThat(registry.isEmpty()).isTrue();
        assertThat(registry.getTunnel("default")).isEmpty();
    }

    @Test
    void clearAllTunnels() {
        registry.register(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
        registry.register(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));
        assertThat(registry.size()).isEqualTo(2);

        registry.clear();
        assertThat(registry.isEmpty()).isTrue();
    }

    @Test
    void multipleTunnels() {
        registry.register(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
        registry.register(new NgrokTunnel("frontend", "https://fe.ngrok-free.app", 3000, "http"));
        registry.register(new NgrokTunnel("database", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.getAllTunnels()).hasSize(3);
    }
}
