package com.kermel.ngrok.autoconfigure;

import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.event.NgrokReadyEvent;
import com.kermel.ngrok.event.NgrokTunnelClosedEvent;
import com.kermel.ngrok.event.NgrokTunnelEstablishedEvent;
import com.kermel.ngrok.exception.NgrokTunnelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for multi-tunnel support across the lifecycle,
 * registry, banner, and reconnector.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Multi-Tunnel Support")
class NgrokMultiTunnelTest {

    @Mock
    private NgrokTunnelManager tunnelManager;

    @Mock
    private NgrokBannerPrinter bannerPrinter;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private NgrokTunnelReconnector reconnector;

    @Mock
    private WebServerInitializedEvent webServerEvent;

    @Mock
    private WebServer webServer;

    private NgrokTunnelRegistry tunnelRegistry;
    private NgrokProperties properties;
    private NgrokLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        tunnelRegistry = new NgrokTunnelRegistry();
        properties = new NgrokProperties();

        lifecycle = new NgrokLifecycle(
                tunnelManager, tunnelRegistry, properties,
                bannerPrinter, eventPublisher, reconnector);

        lenient().when(webServerEvent.getWebServer()).thenReturn(webServer);
        lenient().when(webServer.getPort()).thenReturn(8080);
    }

    @Nested
    @DisplayName("Lifecycle — multi-tunnel creation")
    class LifecycleMultiTunnel {

        @Test
        void threeNamedTunnels_allCreated() {
            configureTunnels("api", 8080, "http",
                    "admin", 8080, "http",
                    "db", 5432, "tcp");

            NgrokTunnel api = new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http");
            NgrokTunnel admin = new NgrokTunnel("admin", "https://admin.ngrok-free.app", 8080, "http");
            NgrokTunnel db = new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp");
            when(tunnelManager.createTunnel(eq("api"), any())).thenReturn(api);
            when(tunnelManager.createTunnel(eq("admin"), any())).thenReturn(admin);
            when(tunnelManager.createTunnel(eq("db"), any())).thenReturn(db);

            lifecycle.onApplicationEvent(webServerEvent);

            assertThat(tunnelRegistry.size()).isEqualTo(3);
            assertThat(tunnelRegistry.getPublicUrl("api")).isEqualTo("https://api.ngrok-free.app");
            assertThat(tunnelRegistry.getPublicUrl("admin")).isEqualTo("https://admin.ngrok-free.app");
            assertThat(tunnelRegistry.getPublicUrl("db")).isEqualTo("tcp://0.tcp.ngrok.io:12345");
        }

        @Test
        void mixedProtocols_httpAndTcp() {
            configureTunnels("web", 8080, "http",
                    "tcp-service", 9090, "tcp");

            NgrokTunnel web = new NgrokTunnel("web", "https://web.ngrok-free.app", 8080, "http");
            NgrokTunnel tcp = new NgrokTunnel("tcp-service", "tcp://0.tcp.ngrok.io:54321", 9090, "tcp");
            when(tunnelManager.createTunnel(eq("web"), any())).thenReturn(web);
            when(tunnelManager.createTunnel(eq("tcp-service"), any())).thenReturn(tcp);

            lifecycle.onApplicationEvent(webServerEvent);

            assertThat(tunnelRegistry.size()).isEqualTo(2);
            assertThat(tunnelRegistry.getTunnel("web")).isPresent();
            assertThat(tunnelRegistry.getTunnel("tcp-service")).isPresent();
        }

        @Test
        void eachTunnelPublishesEstablishedEvent() {
            configureTunnels("api", 8080, "http",
                    "admin", 8080, "http");

            NgrokTunnel api = new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http");
            NgrokTunnel admin = new NgrokTunnel("admin", "https://admin.ngrok-free.app", 8080, "http");
            when(tunnelManager.createTunnel(eq("api"), any())).thenReturn(api);
            when(tunnelManager.createTunnel(eq("admin"), any())).thenReturn(admin);

            lifecycle.onApplicationEvent(webServerEvent);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher, times(3)).publishEvent(captor.capture());

            List<Object> events = captor.getAllValues();
            long establishedCount = events.stream()
                    .filter(e -> e instanceof NgrokTunnelEstablishedEvent)
                    .count();
            long readyCount = events.stream()
                    .filter(e -> e instanceof NgrokReadyEvent)
                    .count();

            assertThat(establishedCount).isEqualTo(2);
            assertThat(readyCount).isEqualTo(1);
        }

        @Test
        void readyEventContainsAllTunnels() {
            configureTunnels("api", 8080, "http",
                    "admin", 8080, "http",
                    "db", 5432, "tcp");

            when(tunnelManager.createTunnel(eq("api"), any()))
                    .thenReturn(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            when(tunnelManager.createTunnel(eq("admin"), any()))
                    .thenReturn(new NgrokTunnel("admin", "https://admin.ngrok-free.app", 8080, "http"));
            when(tunnelManager.createTunnel(eq("db"), any()))
                    .thenReturn(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

            lifecycle.onApplicationEvent(webServerEvent);

            ArgumentCaptor<NgrokReadyEvent> captor = ArgumentCaptor.forClass(NgrokReadyEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            NgrokReadyEvent readyEvent = captor.getValue();
            assertThat(readyEvent.getTunnels()).hasSize(3);
        }

        @Test
        void bannerPrinterReceivesAllTunnels() {
            configureTunnels("api", 8080, "http",
                    "db", 5432, "tcp");

            when(tunnelManager.createTunnel(eq("api"), any()))
                    .thenReturn(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            when(tunnelManager.createTunnel(eq("db"), any()))
                    .thenReturn(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

            lifecycle.onApplicationEvent(webServerEvent);

            ArgumentCaptor<java.util.Collection<NgrokTunnel>> captor =
                    ArgumentCaptor.forClass(java.util.Collection.class);
            verify(bannerPrinter).print(captor.capture());

            assertThat(captor.getValue()).hasSize(2);
        }

        @Test
        void reconnectorConfiguredForEachTunnel() {
            configureTunnels("api", 8080, "http",
                    "admin", 8080, "http",
                    "db", 5432, "tcp");

            when(tunnelManager.createTunnel(eq("api"), any()))
                    .thenReturn(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            when(tunnelManager.createTunnel(eq("admin"), any()))
                    .thenReturn(new NgrokTunnel("admin", "https://admin.ngrok-free.app", 8080, "http"));
            when(tunnelManager.createTunnel(eq("db"), any()))
                    .thenReturn(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

            lifecycle.onApplicationEvent(webServerEvent);

            verify(reconnector).registerTunnelConfig(eq("api"), any());
            verify(reconnector).registerTunnelConfig(eq("admin"), any());
            verify(reconnector).registerTunnelConfig(eq("db"), any());
            verify(reconnector).start();
        }

        @Test
        void serverPortUsedForTunnelsWithoutExplicitPort() {
            NgrokProperties.TunnelProperties apiConfig = new NgrokProperties.TunnelProperties();
            // No port set — should inherit from detected server port
            properties.getTunnels().put("api", apiConfig);

            NgrokProperties.TunnelProperties dbConfig = new NgrokProperties.TunnelProperties();
            dbConfig.setPort(5432); // Explicit port
            dbConfig.setProtocol("tcp");
            properties.getTunnels().put("db", dbConfig);

            when(tunnelManager.createTunnel(eq("api"), any()))
                    .thenReturn(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            when(tunnelManager.createTunnel(eq("db"), any()))
                    .thenReturn(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

            lifecycle.onApplicationEvent(webServerEvent);

            // api tunnel should have inherited server port 8080
            assertThat(apiConfig.getPort()).isEqualTo(8080);
            // db tunnel should keep its explicit port
            assertThat(dbConfig.getPort()).isEqualTo(5432);
        }
    }

    @Nested
    @DisplayName("Lifecycle — partial failure")
    class PartialFailure {

        @Test
        void failOpenTunnel_otherTunnelsStillCreated() {
            NgrokProperties.TunnelProperties apiConfig = new NgrokProperties.TunnelProperties();
            apiConfig.setPort(8080);
            apiConfig.setFailOpen(true);
            properties.getTunnels().put("api", apiConfig);

            NgrokProperties.TunnelProperties dbConfig = new NgrokProperties.TunnelProperties();
            dbConfig.setPort(5432);
            dbConfig.setProtocol("tcp");
            properties.getTunnels().put("db", dbConfig);

            when(tunnelManager.createTunnel(eq("api"), any()))
                    .thenThrow(new NgrokTunnelException("API tunnel failed"));
            when(tunnelManager.createTunnel(eq("db"), any()))
                    .thenReturn(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

            lifecycle.onApplicationEvent(webServerEvent);

            assertThat(tunnelRegistry.size()).isEqualTo(1);
            assertThat(tunnelRegistry.getTunnel("db")).isPresent();
            assertThat(tunnelRegistry.getTunnel("api")).isEmpty();
            assertThat(lifecycle.isRunning()).isTrue();
        }

        @Test
        void failClosedTunnel_stopsEntireStartup() {
            NgrokProperties.TunnelProperties apiConfig = new NgrokProperties.TunnelProperties();
            apiConfig.setPort(8080);
            apiConfig.setFailOpen(false);
            properties.getTunnels().put("api", apiConfig);

            NgrokProperties.TunnelProperties dbConfig = new NgrokProperties.TunnelProperties();
            dbConfig.setPort(5432);
            dbConfig.setProtocol("tcp");
            properties.getTunnels().put("db", dbConfig);

            when(tunnelManager.createTunnel(eq("api"), any()))
                    .thenThrow(new NgrokTunnelException("API tunnel failed"));

            assertThatThrownBy(() -> lifecycle.onApplicationEvent(webServerEvent))
                    .isInstanceOf(NgrokTunnelException.class)
                    .hasMessageContaining("API tunnel failed");
        }

        @Test
        void allTunnelsFailOpen_noReadyEvent() {
            NgrokProperties.TunnelProperties apiConfig = new NgrokProperties.TunnelProperties();
            apiConfig.setPort(8080);
            apiConfig.setFailOpen(true);
            properties.getTunnels().put("api", apiConfig);

            NgrokProperties.TunnelProperties adminConfig = new NgrokProperties.TunnelProperties();
            adminConfig.setPort(8080);
            adminConfig.setFailOpen(true);
            properties.getTunnels().put("admin", adminConfig);

            when(tunnelManager.createTunnel(any(), any()))
                    .thenThrow(new NgrokTunnelException("Connection failed"));

            lifecycle.onApplicationEvent(webServerEvent);

            assertThat(tunnelRegistry.isEmpty()).isTrue();
            verify(eventPublisher, never()).publishEvent(any(NgrokReadyEvent.class));
            verify(bannerPrinter, never()).print(any());
        }

        @Test
        void mixedFailOpenAndFailClosed_partialSuccess() {
            NgrokProperties.TunnelProperties apiConfig = new NgrokProperties.TunnelProperties();
            apiConfig.setPort(8080);
            apiConfig.setFailOpen(true);  // won't throw
            properties.getTunnels().put("api", apiConfig);

            NgrokProperties.TunnelProperties adminConfig = new NgrokProperties.TunnelProperties();
            adminConfig.setPort(8080);
            properties.getTunnels().put("admin", adminConfig);

            when(tunnelManager.createTunnel(eq("api"), any()))
                    .thenThrow(new NgrokTunnelException("API failed"));
            when(tunnelManager.createTunnel(eq("admin"), any()))
                    .thenReturn(new NgrokTunnel("admin", "https://admin.ngrok-free.app", 8080, "http"));

            lifecycle.onApplicationEvent(webServerEvent);

            assertThat(tunnelRegistry.size()).isEqualTo(1);
            assertThat(tunnelRegistry.getTunnel("admin")).isPresent();
            // Ready event should still fire because at least one tunnel succeeded
            verify(eventPublisher).publishEvent(any(NgrokReadyEvent.class));
        }
    }

    @Nested
    @DisplayName("Lifecycle — shutdown with multiple tunnels")
    class MultiTunnelShutdown {

        @Test
        void stopPublishesCloseEventForEachTunnel() {
            configureTunnels("api", 8080, "http",
                    "admin", 8080, "http",
                    "db", 5432, "tcp");

            when(tunnelManager.createTunnel(eq("api"), any()))
                    .thenReturn(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            when(tunnelManager.createTunnel(eq("admin"), any()))
                    .thenReturn(new NgrokTunnel("admin", "https://admin.ngrok-free.app", 8080, "http"));
            when(tunnelManager.createTunnel(eq("db"), any()))
                    .thenReturn(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

            lifecycle.onApplicationEvent(webServerEvent);
            reset(eventPublisher);

            lifecycle.stop();

            ArgumentCaptor<NgrokTunnelClosedEvent> captor =
                    ArgumentCaptor.forClass(NgrokTunnelClosedEvent.class);
            verify(eventPublisher, times(3)).publishEvent(captor.capture());

            List<String> closedNames = captor.getAllValues().stream()
                    .map(e -> e.getTunnel().name())
                    .toList();
            assertThat(closedNames).containsExactlyInAnyOrder("api", "admin", "db");
        }

        @Test
        void stopClearsRegistryAndStopsReconnector() {
            configureTunnels("api", 8080, "http",
                    "db", 5432, "tcp");

            when(tunnelManager.createTunnel(eq("api"), any()))
                    .thenReturn(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            when(tunnelManager.createTunnel(eq("db"), any()))
                    .thenReturn(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

            lifecycle.onApplicationEvent(webServerEvent);
            assertThat(tunnelRegistry.size()).isEqualTo(2);

            lifecycle.stop();

            assertThat(tunnelRegistry.isEmpty()).isTrue();
            verify(reconnector).stop();
            verify(tunnelManager).shutdown();
        }
    }

    @Nested
    @DisplayName("Registry — multi-tunnel operations")
    class RegistryMultiTunnel {

        @Test
        void defaultTunnelFallsBackToFirstWhenNoDefault() {
            tunnelRegistry.register(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            tunnelRegistry.register(new NgrokTunnel("admin", "https://admin.ngrok-free.app", 8080, "http"));

            // No tunnel named "default" — should return the first registered
            NgrokTunnel defaultTunnel = tunnelRegistry.getDefaultTunnel();
            assertThat(defaultTunnel).isNotNull();
        }

        @Test
        void defaultTunnelPreferredWhenPresent() {
            tunnelRegistry.register(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            tunnelRegistry.register(new NgrokTunnel("default", "https://def.ngrok-free.app", 8080, "http"));

            assertThat(tunnelRegistry.getDefaultTunnel().name()).isEqualTo("default");
        }

        @Test
        void getPublicUrlReturnsCorrectUrlPerTunnel() {
            tunnelRegistry.register(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            tunnelRegistry.register(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));
            tunnelRegistry.register(new NgrokTunnel("tls", "tls://secure.ngrok.io:443", 443, "tls"));

            assertThat(tunnelRegistry.getPublicUrl("api")).startsWith("https://");
            assertThat(tunnelRegistry.getPublicUrl("db")).startsWith("tcp://");
            assertThat(tunnelRegistry.getPublicUrl("tls")).startsWith("tls://");
            assertThat(tunnelRegistry.getPublicUrl("nonexistent")).isNull();
        }

        @Test
        void deregisterOneTunnelLeavesOthers() {
            tunnelRegistry.register(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            tunnelRegistry.register(new NgrokTunnel("admin", "https://admin.ngrok-free.app", 8080, "http"));
            tunnelRegistry.register(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

            tunnelRegistry.deregister("admin");

            assertThat(tunnelRegistry.size()).isEqualTo(2);
            assertThat(tunnelRegistry.getTunnel("admin")).isEmpty();
            assertThat(tunnelRegistry.getTunnel("api")).isPresent();
            assertThat(tunnelRegistry.getTunnel("db")).isPresent();
        }

        @Test
        void registerReplacesExistingTunnel() {
            tunnelRegistry.register(new NgrokTunnel("api", "https://old.ngrok-free.app", 8080, "http"));
            tunnelRegistry.register(new NgrokTunnel("api", "https://new.ngrok-free.app", 8080, "http"));

            assertThat(tunnelRegistry.size()).isEqualTo(1);
            assertThat(tunnelRegistry.getPublicUrl("api")).isEqualTo("https://new.ngrok-free.app");
        }

        @Test
        void getAllTunnelsReturnsUnmodifiableCollection() {
            tunnelRegistry.register(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            tunnelRegistry.register(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

            var tunnels = tunnelRegistry.getAllTunnels();
            assertThat(tunnels).hasSize(2);
            assertThatThrownBy(() -> tunnels.add(
                    new NgrokTunnel("hack", "https://hack.ngrok.io", 9999, "http")
            )).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Properties — multi-tunnel config binding")
    class PropertiesMultiTunnel {

        @Test
        void emptyTunnelsMapUsesDefaultTunnel() {
            assertThat(properties.getTunnels()).isEmpty();
            assertThat(properties.getDefaultTunnel()).isNotNull();
        }

        @Test
        void namedTunnelsWithDifferentConfigs() {
            NgrokProperties.TunnelProperties api = new NgrokProperties.TunnelProperties();
            api.setPort(8080);
            api.setProtocol("http");
            api.setDomain("my-api.ngrok.dev");

            NgrokProperties.TunnelProperties db = new NgrokProperties.TunnelProperties();
            db.setPort(5432);
            db.setProtocol("tcp");
            db.setFailOpen(true);

            NgrokProperties.TunnelProperties admin = new NgrokProperties.TunnelProperties();
            admin.setPort(8080);
            admin.setBasicAuth("admin:password");

            properties.getTunnels().put("api", api);
            properties.getTunnels().put("db", db);
            properties.getTunnels().put("admin", admin);

            assertThat(properties.getTunnels()).hasSize(3);
            assertThat(properties.getTunnels().get("api").getDomain()).isEqualTo("my-api.ngrok.dev");
            assertThat(properties.getTunnels().get("db").getProtocol()).isEqualTo("tcp");
            assertThat(properties.getTunnels().get("db").isFailOpen()).isTrue();
            assertThat(properties.getTunnels().get("admin").getBasicAuth()).isEqualTo("admin:password");
        }

        @Test
        void perTunnelTrafficPolicy() {
            NgrokProperties.TunnelProperties apiConfig = new NgrokProperties.TunnelProperties();
            apiConfig.setTrafficPolicy("on_http_request:\n  - actions:\n    - type: rate-limit");

            NgrokProperties.TunnelProperties webConfig = new NgrokProperties.TunnelProperties();
            webConfig.setTrafficPolicyFile("classpath:policies/web-policy.yml");

            properties.getTunnels().put("api", apiConfig);
            properties.getTunnels().put("web", webConfig);

            assertThat(properties.getTunnels().get("api").getTrafficPolicy()).contains("rate-limit");
            assertThat(properties.getTunnels().get("web").getTrafficPolicyFile()).contains("web-policy.yml");
        }

        @Test
        void perTunnelIpRestrictions() {
            NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
            config.setAllowCidrs(List.of("10.0.0.0/8", "192.168.1.0/24"));
            config.setDenyCidrs(List.of("0.0.0.0/0"));

            properties.getTunnels().put("restricted", config);

            assertThat(properties.getTunnels().get("restricted").getAllowCidrs()).hasSize(2);
            assertThat(properties.getTunnels().get("restricted").getDenyCidrs()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Banner — multi-tunnel display")
    class BannerMultiTunnel {

        @Test
        void bannerPrinterCalledWithMultipleTunnels() {
            configureTunnels("api", 8080, "http",
                    "db", 5432, "tcp");

            when(tunnelManager.createTunnel(eq("api"), any()))
                    .thenReturn(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
            when(tunnelManager.createTunnel(eq("db"), any()))
                    .thenReturn(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

            lifecycle.onApplicationEvent(webServerEvent);

            verify(bannerPrinter).print(argThat(tunnels -> tunnels.size() == 2));
        }
    }

    // --- Helper methods ---

    private void configureTunnels(Object... args) {
        for (int i = 0; i < args.length; i += 3) {
            String name = (String) args[i];
            int port = (int) args[i + 1];
            String protocol = (String) args[i + 2];

            NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
            config.setPort(port);
            config.setProtocol(protocol);
            properties.getTunnels().put(name, config);
        }
    }
}
