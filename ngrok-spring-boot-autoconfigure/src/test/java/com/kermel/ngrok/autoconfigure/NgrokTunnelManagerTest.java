package com.kermel.ngrok.autoconfigure;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Tunnel;
import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.exception.NgrokBinaryException;
import com.kermel.ngrok.exception.NgrokPortConflictException;
import com.kermel.ngrok.exception.NgrokTunnelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NgrokTunnelManagerTest {

    @Mock
    private NgrokClient ngrokClient;

    @Mock
    private Tunnel javaNgrokTunnel;

    private NgrokProperties properties;
    private NgrokTunnelManager tunnelManager;

    @BeforeEach
    void setUp() {
        properties = new NgrokProperties();
        tunnelManager = new NgrokTunnelManager(ngrokClient, properties);
    }

    @Test
    void createHttpTunnel() {
        when(javaNgrokTunnel.getPublicUrl()).thenReturn("https://abc123.ngrok-free.app");
        when(ngrokClient.connect(any(CreateTunnel.class))).thenReturn(javaNgrokTunnel);

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(8080);
        config.setProtocol("http");

        NgrokTunnel tunnel = tunnelManager.createTunnel("default", config);

        assertThat(tunnel.name()).isEqualTo("default");
        assertThat(tunnel.publicUrl()).isEqualTo("https://abc123.ngrok-free.app");
        assertThat(tunnel.localPort()).isEqualTo(8080);
        assertThat(tunnel.protocol()).isEqualTo("http");
        assertThat(tunnel.trafficPolicyEnabled()).isFalse();
    }

    @Test
    void createTcpTunnel() {
        when(javaNgrokTunnel.getPublicUrl()).thenReturn("tcp://0.tcp.ngrok.io:12345");
        when(ngrokClient.connect(any(CreateTunnel.class))).thenReturn(javaNgrokTunnel);

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(5432);
        config.setProtocol("tcp");

        NgrokTunnel tunnel = tunnelManager.createTunnel("database", config);

        assertThat(tunnel.name()).isEqualTo("database");
        assertThat(tunnel.publicUrl()).isEqualTo("tcp://0.tcp.ngrok.io:12345");
        assertThat(tunnel.localPort()).isEqualTo(5432);
        assertThat(tunnel.protocol()).isEqualTo("tcp");
    }

    @Test
    void createTunnelWithDomain() {
        when(javaNgrokTunnel.getPublicUrl()).thenReturn("https://my-app.ngrok.dev");
        when(ngrokClient.connect(any(CreateTunnel.class))).thenReturn(javaNgrokTunnel);

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(8080);
        config.setDomain("my-app.ngrok.dev");

        NgrokTunnel tunnel = tunnelManager.createTunnel("default", config);

        assertThat(tunnel.publicUrl()).isEqualTo("https://my-app.ngrok.dev");
        assertThat(tunnel.domain()).isEqualTo("my-app.ngrok.dev");
    }

    @Test
    void createTunnelWithInlineTrafficPolicy() {
        when(javaNgrokTunnel.getPublicUrl()).thenReturn("https://abc123.ngrok-free.app");
        when(ngrokClient.connect(any(CreateTunnel.class))).thenReturn(javaNgrokTunnel);

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(8080);
        config.setTrafficPolicy("on_http_request:\n  - name: test\n    actions:\n      - type: deny");

        NgrokTunnel tunnel = tunnelManager.createTunnel("default", config);

        assertThat(tunnel.trafficPolicyEnabled()).isTrue();
    }

    @Test
    void createTunnelWithRetrySucceedsOnSecondAttempt() {
        when(ngrokClient.connect(any(CreateTunnel.class)))
                .thenThrow(new RuntimeException("Temporary failure"))
                .thenReturn(javaNgrokTunnel);
        when(javaNgrokTunnel.getPublicUrl()).thenReturn("https://abc123.ngrok-free.app");

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(8080);

        NgrokTunnel tunnel = tunnelManager.createTunnelWithRetry("default", config, 2, 10);

        assertThat(tunnel.publicUrl()).isEqualTo("https://abc123.ngrok-free.app");
        verify(ngrokClient, times(2)).connect(any(CreateTunnel.class));
    }

    @Test
    void createTunnelWithRetryExhaustsAttempts() {
        when(ngrokClient.connect(any(CreateTunnel.class)))
                .thenThrow(new RuntimeException("Persistent failure"));

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(8080);

        assertThatThrownBy(() -> tunnelManager.createTunnelWithRetry("default", config, 2, 10))
                .isInstanceOf(NgrokTunnelException.class)
                .hasMessageContaining("Failed to create ngrok tunnel 'default'");

        // 1 initial + 2 retries = 3 attempts
        verify(ngrokClient, times(3)).connect(any(CreateTunnel.class));
    }

    @Test
    void portConflictNotRetried() {
        when(ngrokClient.connect(any(CreateTunnel.class)))
                .thenThrow(new RuntimeException("address already in use"));

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(8080);

        assertThatThrownBy(() -> tunnelManager.createTunnel("default", config))
                .isInstanceOf(NgrokPortConflictException.class);

        // Port conflict should not be retried, so only 1 attempt
        verify(ngrokClient, times(1)).connect(any(CreateTunnel.class));
    }

    @Test
    void binaryNotFoundNotRetried() {
        when(ngrokClient.connect(any(CreateTunnel.class)))
                .thenThrow(new RuntimeException("ngrok not found in PATH"));

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(8080);

        assertThatThrownBy(() -> tunnelManager.createTunnel("default", config))
                .isInstanceOf(NgrokBinaryException.class)
                .hasMessageContaining("ngrok binary not found");

        verify(ngrokClient, times(1)).connect(any(CreateTunnel.class));
    }

    @Test
    void createTunnelFailureThrowsException() {
        when(ngrokClient.connect(any(CreateTunnel.class))).thenThrow(new RuntimeException("Connection refused"));

        NgrokProperties.TunnelProperties config = new NgrokProperties.TunnelProperties();
        config.setPort(8080);

        assertThatThrownBy(() -> tunnelManager.createTunnelWithRetry("default", config, 0, 0))
                .isInstanceOf(NgrokTunnelException.class)
                .hasMessageContaining("Failed to create ngrok tunnel 'default'")
                .hasMessageContaining("Connection refused");
    }

    @Test
    void shutdown() {
        tunnelManager.shutdown();

        verify(ngrokClient).kill();
    }

    @Test
    void shutdownHandlesErrors() {
        doThrow(new RuntimeException("Process not running")).when(ngrokClient).kill();

        // Should not throw
        tunnelManager.shutdown();

        verify(ngrokClient).kill();
    }
}
