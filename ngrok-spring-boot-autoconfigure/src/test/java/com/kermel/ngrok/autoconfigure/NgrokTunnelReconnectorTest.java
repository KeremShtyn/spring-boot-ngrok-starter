package com.kermel.ngrok.autoconfigure;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Tunnel;
import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NgrokTunnelReconnectorTest {

    @Mock
    private NgrokClient ngrokClient;

    @Mock
    private NgrokTunnelManager tunnelManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Tunnel javaNgrokTunnel;

    private NgrokTunnelRegistry tunnelRegistry;
    private NgrokProperties properties;
    private NgrokTunnelReconnector reconnector;

    @BeforeEach
    void setUp() {
        tunnelRegistry = new NgrokTunnelRegistry();
        properties = new NgrokProperties();
        properties.getReconnection().setEnabled(true);
        properties.getReconnection().setMaxAttempts(2);
        properties.getReconnection().setInitialDelaySeconds(0);
        properties.getReconnection().setCheckIntervalSeconds(1);

        reconnector = new NgrokTunnelReconnector(
                ngrokClient, tunnelManager, tunnelRegistry, properties, eventPublisher);
    }

    @Test
    void detectsDroppedTunnelAndReconnects() {
        // Register a tunnel
        NgrokTunnel original = new NgrokTunnel("default", "https://abc123.ngrok-free.app", 8080, "http");
        tunnelRegistry.register(original);

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(8080);
        reconnector.registerTunnelConfig("default", config);

        // ngrok reports no active tunnels (tunnel dropped)
        when(ngrokClient.getTunnels()).thenReturn(List.of());

        // Reconnection succeeds
        NgrokTunnel reconnected = new NgrokTunnel("default", "https://def456.ngrok-free.app", 8080, "http");
        when(tunnelManager.createTunnel("default", config)).thenReturn(reconnected);

        // Trigger check
        reconnector.checkTunnels();

        assertThat(reconnector.getTotalReconnections()).isEqualTo(1);
        assertThat(reconnector.getFailedReconnections()).isZero();
        assertThat(tunnelRegistry.getPublicUrl()).isEqualTo("https://def456.ngrok-free.app");
        // close event for old + established event for new
        verify(eventPublisher, times(2)).publishEvent(any());
    }

    @Test
    void doesNotReconnectIfTunnelStillActive() {
        NgrokTunnel tunnel = new NgrokTunnel("default", "https://abc123.ngrok-free.app", 8080, "http");
        tunnelRegistry.register(tunnel);

        // ngrok reports the tunnel as still active
        when(javaNgrokTunnel.getPublicUrl()).thenReturn("https://abc123.ngrok-free.app");
        when(ngrokClient.getTunnels()).thenReturn(List.of(javaNgrokTunnel));

        reconnector.checkTunnels();

        assertThat(reconnector.getTotalReconnections()).isZero();
        verify(tunnelManager, never()).createTunnel(anyString(), any());
    }

    @Test
    void reconnectionFailureIncreasesFailedCount() {
        NgrokTunnel original = new NgrokTunnel("default", "https://abc123.ngrok-free.app", 8080, "http");
        tunnelRegistry.register(original);

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(8080);
        reconnector.registerTunnelConfig("default", config);

        when(ngrokClient.getTunnels()).thenReturn(List.of());
        when(tunnelManager.createTunnel("default", config))
                .thenThrow(new RuntimeException("Connection failed"));

        reconnector.checkTunnels();

        assertThat(reconnector.getFailedReconnections()).isEqualTo(1);
        assertThat(reconnector.getTotalReconnections()).isZero();
    }

    @Test
    void noOpWhenRegistryIsEmpty() {
        reconnector.checkTunnels();

        verify(ngrokClient, never()).getTunnels();
        assertThat(reconnector.getTotalReconnections()).isZero();
    }

    @Test
    void missingConfigPreventsReconnection() {
        NgrokTunnel original = new NgrokTunnel("orphan", "https://abc123.ngrok-free.app", 8080, "http");
        tunnelRegistry.register(original);
        // Don't register any config for this tunnel

        when(ngrokClient.getTunnels()).thenReturn(List.of());

        reconnector.checkTunnels();

        assertThat(reconnector.getFailedReconnections()).isEqualTo(1);
        verify(tunnelManager, never()).createTunnel(anyString(), any());
    }

    @Test
    void disabledReconnectorDoesNotStart() {
        properties.getReconnection().setEnabled(false);
        NgrokTunnelReconnector disabled = new NgrokTunnelReconnector(
                ngrokClient, tunnelManager, tunnelRegistry, properties, eventPublisher);

        assertThat(disabled.isEnabled()).isFalse();
        // start() should be a no-op
        disabled.start();
        disabled.stop();
    }
}
