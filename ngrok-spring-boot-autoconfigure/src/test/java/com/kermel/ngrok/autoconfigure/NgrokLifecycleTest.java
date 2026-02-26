package com.kermel.ngrok.autoconfigure;

import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.event.NgrokReadyEvent;
import com.kermel.ngrok.event.NgrokTunnelClosedEvent;
import com.kermel.ngrok.event.NgrokTunnelEstablishedEvent;
import com.kermel.ngrok.exception.NgrokTunnelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.boot.web.server.WebServer;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NgrokLifecycleTest {

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

        when(webServerEvent.getWebServer()).thenReturn(webServer);
        when(webServer.getPort()).thenReturn(8080);
    }

    @Test
    void createsSingleDefaultTunnel() {
        NgrokTunnel tunnel = new NgrokTunnel("default", "https://abc.ngrok-free.app", 8080, "http");
        when(tunnelManager.createTunnel(eq("default"), any())).thenReturn(tunnel);

        lifecycle.onApplicationEvent(webServerEvent);

        assertThat(lifecycle.isRunning()).isTrue();
        assertThat(tunnelRegistry.size()).isEqualTo(1);
        assertThat(tunnelRegistry.getPublicUrl()).isEqualTo("https://abc.ngrok-free.app");

        verify(bannerPrinter).print(any());
        verify(reconnector).start();
        verify(reconnector).registerTunnelConfig(eq("default"), any());
    }

    @Test
    void publishesEvents() {
        NgrokTunnel tunnel = new NgrokTunnel("default", "https://abc.ngrok-free.app", 8080, "http");
        when(tunnelManager.createTunnel(eq("default"), any())).thenReturn(tunnel);

        lifecycle.onApplicationEvent(webServerEvent);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues().get(0)).isInstanceOf(NgrokTunnelEstablishedEvent.class);
        assertThat(eventCaptor.getAllValues().get(1)).isInstanceOf(NgrokReadyEvent.class);
    }

    @Test
    void failOpenContinuesWithoutTunnel() {
        properties.getDefaultTunnel().setFailOpen(true);
        when(tunnelManager.createTunnel(eq("default"), any()))
                .thenThrow(new NgrokTunnelException("Connection failed"));

        lifecycle.onApplicationEvent(webServerEvent);

        assertThat(lifecycle.isRunning()).isTrue();
        assertThat(tunnelRegistry.isEmpty()).isTrue();
        // No ready event when no tunnels were created
        verify(eventPublisher, never()).publishEvent(any(NgrokReadyEvent.class));
    }

    @Test
    void failClosedThrowsException() {
        properties.getDefaultTunnel().setFailOpen(false);
        when(tunnelManager.createTunnel(eq("default"), any()))
                .thenThrow(new NgrokTunnelException("Connection failed"));

        assertThatThrownBy(() -> lifecycle.onApplicationEvent(webServerEvent))
                .isInstanceOf(NgrokTunnelException.class)
                .hasMessageContaining("Connection failed");
    }

    @Test
    void createsMultipleTunnels() {
        NgrokProperties.TunnelProperties apiConfig = new NgrokProperties.TunnelProperties();
        apiConfig.setPort(8080);
        properties.getTunnels().put("api", apiConfig);

        NgrokProperties.TunnelProperties dbConfig = new NgrokProperties.TunnelProperties();
        dbConfig.setPort(5432);
        dbConfig.setProtocol("tcp");
        properties.getTunnels().put("db", dbConfig);

        NgrokTunnel api = new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http");
        NgrokTunnel db = new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp");
        when(tunnelManager.createTunnel(eq("api"), any())).thenReturn(api);
        when(tunnelManager.createTunnel(eq("db"), any())).thenReturn(db);

        lifecycle.onApplicationEvent(webServerEvent);

        assertThat(tunnelRegistry.size()).isEqualTo(2);
        verify(reconnector).registerTunnelConfig(eq("api"), any());
        verify(reconnector).registerTunnelConfig(eq("db"), any());
    }

    @Test
    void stopPublishesCloseEventsAndShutsDown() {
        NgrokTunnel tunnel = new NgrokTunnel("default", "https://abc.ngrok-free.app", 8080, "http");
        when(tunnelManager.createTunnel(eq("default"), any())).thenReturn(tunnel);

        lifecycle.onApplicationEvent(webServerEvent);
        lifecycle.stop();

        assertThat(lifecycle.isRunning()).isFalse();
        assertThat(tunnelRegistry.isEmpty()).isTrue();
        verify(reconnector).stop();
        verify(tunnelManager).shutdown();
        verify(eventPublisher).publishEvent(any(NgrokTunnelClosedEvent.class));
    }

    @Test
    void stopIsIdempotent() {
        lifecycle.stop();
        lifecycle.stop();
        // Should not throw or call shutdown multiple times
        verify(tunnelManager, never()).shutdown();
    }

    @Test
    void defaultPortUsedWhenNotConfigured() {
        NgrokTunnel tunnel = new NgrokTunnel("default", "https://abc.ngrok-free.app", 8080, "http");
        when(tunnelManager.createTunnel(eq("default"), any())).thenReturn(tunnel);

        // server.port not set in tunnel config — should use the detected port
        assertThat(properties.getDefaultTunnel().getPort()).isNull();

        lifecycle.onApplicationEvent(webServerEvent);

        assertThat(properties.getDefaultTunnel().getPort()).isEqualTo(8080);
    }
}
