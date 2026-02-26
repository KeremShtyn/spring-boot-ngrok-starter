package com.kermel.ngrok.actuator;

import com.kermel.ngrok.autoconfigure.NgrokProperties;
import com.kermel.ngrok.autoconfigure.NgrokTunnelReconnector;
import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NgrokEndpointTest {

    @Mock
    private NgrokTunnelReconnector reconnector;

    private NgrokTunnelRegistry tunnelRegistry;
    private NgrokEndpoint endpoint;

    @BeforeEach
    void setUp() {
        tunnelRegistry = new NgrokTunnelRegistry();
        when(reconnector.isEnabled()).thenReturn(true);
        when(reconnector.getTotalReconnections()).thenReturn(0);
        when(reconnector.getFailedReconnections()).thenReturn(0);
        endpoint = new NgrokEndpoint(tunnelRegistry, reconnector, new NgrokProperties());
    }

    @Test
    void stoppedWhenNoTunnels() {
        Map<String, Object> info = endpoint.ngrokInfo();

        assertThat(info.get("status")).isEqualTo("stopped");
        assertThat((List<?>) info.get("tunnels")).isEmpty();
        assertThat(info).containsKey("reconnection");
    }

    @Test
    @SuppressWarnings("unchecked")
    void runningWithTunnel() {
        tunnelRegistry.register(new NgrokTunnel("default", "https://abc.ngrok-free.app", 8080, "http"));

        Map<String, Object> info = endpoint.ngrokInfo();

        assertThat(info.get("status")).isEqualTo("running");
        assertThat(info).containsKey("uptime");
        assertThat(info.get("inspectionUrl")).isEqualTo("http://localhost:4040");

        List<Map<String, Object>> tunnels = (List<Map<String, Object>>) info.get("tunnels");
        assertThat(tunnels).hasSize(1);
        assertThat(tunnels.get(0).get("name")).isEqualTo("default");
        assertThat(tunnels.get(0).get("publicUrl")).isEqualTo("https://abc.ngrok-free.app");
        assertThat(tunnels.get(0)).containsKey("tunnelUptime");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconnectionInfoIncluded() {
        when(reconnector.getTotalReconnections()).thenReturn(3);
        when(reconnector.getFailedReconnections()).thenReturn(1);

        tunnelRegistry.register(new NgrokTunnel("default", "https://abc.ngrok-free.app", 8080, "http"));
        Map<String, Object> info = endpoint.ngrokInfo();

        Map<String, Object> reconnection = (Map<String, Object>) info.get("reconnection");
        assertThat(reconnection.get("enabled")).isEqualTo(true);
        assertThat(reconnection.get("totalReconnections")).isEqualTo(3);
        assertThat(reconnection.get("failedReconnections")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void multipleTunnelsListed() {
        tunnelRegistry.register(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
        tunnelRegistry.register(new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp"));

        Map<String, Object> info = endpoint.ngrokInfo();

        List<Map<String, Object>> tunnels = (List<Map<String, Object>>) info.get("tunnels");
        assertThat(tunnels).hasSize(2);
    }
}
