package com.kermel.ngrok.actuator;

import com.kermel.ngrok.autoconfigure.NgrokProperties;
import com.kermel.ngrok.autoconfigure.NgrokTunnelReconnector;
import com.kermel.ngrok.core.NgrokStarterVersion;
import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Actuator endpoint at {@code /actuator/ngrok} that exposes tunnel information,
 * uptime, and reconnection statistics.
 */
@Endpoint(id = "ngrok")
public class NgrokEndpoint {

    private final NgrokTunnelRegistry tunnelRegistry;
    private final NgrokTunnelReconnector reconnector;
    private final NgrokProperties properties;
    private final Instant startedAt;

    public NgrokEndpoint(NgrokTunnelRegistry tunnelRegistry, NgrokTunnelReconnector reconnector,
                         NgrokProperties properties) {
        this.tunnelRegistry = tunnelRegistry;
        this.reconnector = reconnector;
        this.properties = properties;
        this.startedAt = Instant.now();
    }

    @ReadOperation
    public Map<String, Object> ngrokInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        // Snapshot the tunnels once to avoid TOCTOU race with the reconnector
        List<NgrokTunnel> tunnels = List.copyOf(tunnelRegistry.getAllTunnels());

        if (tunnels.isEmpty()) {
            info.put("status", "stopped");
            info.put("tunnels", List.of());
            info.put("reconnection", reconnectorInfo());
            return info;
        }

        info.put("status", "running");
        info.put("version", NgrokStarterVersion.getVersion());
        info.put("uptime", formatDuration(Duration.between(startedAt, Instant.now())));
        info.put("inspectionUrl", "http://localhost:" + properties.getInspection().getPort());

        List<Map<String, Object>> tunnelList = tunnels.stream()
                .map(this::tunnelToMap)
                .toList();

        info.put("tunnels", tunnelList);
        info.put("reconnection", reconnectorInfo());
        return info;
    }

    private Map<String, Object> tunnelToMap(NgrokTunnel tunnel) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", tunnel.name());
        map.put("publicUrl", tunnel.publicUrl());
        map.put("localPort", tunnel.localPort());
        map.put("protocol", tunnel.protocol());
        map.put("createdAt", tunnel.createdAt().toString());
        map.put("tunnelUptime", formatDuration(Duration.between(tunnel.createdAt(), Instant.now())));
        if (tunnel.domain() != null) {
            map.put("domain", tunnel.domain());
        }
        map.put("trafficPolicyEnabled", tunnel.trafficPolicyEnabled());
        return map;
    }

    private Map<String, Object> reconnectorInfo() {
        Map<String, Object> reconnection = new LinkedHashMap<>();
        reconnection.put("enabled", reconnector.isEnabled());
        reconnection.put("totalReconnections", reconnector.getTotalReconnections());
        reconnection.put("failedReconnections", reconnector.getFailedReconnections());
        return reconnection;
    }

    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
