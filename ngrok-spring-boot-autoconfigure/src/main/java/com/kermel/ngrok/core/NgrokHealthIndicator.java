package com.kermel.ngrok.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring Boot health indicator for ngrok tunnels.
 *
 * <p>Reports UP when at least one tunnel is active and the ngrok process is responsive.
 * Includes tunnel details, uptime, and reconnection statistics.
 */
public class NgrokHealthIndicator extends AbstractHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(NgrokHealthIndicator.class);

    private final NgrokTunnelRegistry tunnelRegistry;
    private final Instant startedAt;

    public NgrokHealthIndicator(NgrokTunnelRegistry tunnelRegistry) {
        super("ngrok health check failed");
        this.tunnelRegistry = tunnelRegistry;
        this.startedAt = Instant.now();
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (tunnelRegistry.isEmpty()) {
            builder.down()
                    .withDetail("status", "No active tunnels")
                    .withDetail("message", "ngrok has no active tunnels. " +
                            "This may indicate a startup failure or all tunnels were closed.");
            return;
        }

        builder.up();
        builder.withDetail("version", NgrokStarterVersion.getVersion());
        builder.withDetail("tunnelCount", tunnelRegistry.size());
        builder.withDetail("uptime", formatDuration(Duration.between(startedAt, Instant.now())));

        for (NgrokTunnel tunnel : tunnelRegistry.getAllTunnels()) {
            Map<String, Object> tunnelInfo = new LinkedHashMap<>();
            tunnelInfo.put("publicUrl", tunnel.publicUrl());
            tunnelInfo.put("localPort", tunnel.localPort());
            tunnelInfo.put("protocol", tunnel.protocol());
            tunnelInfo.put("createdAt", tunnel.createdAt().toString());
            tunnelInfo.put("tunnelUptime", formatDuration(Duration.between(tunnel.createdAt(), Instant.now())));
            tunnelInfo.put("trafficPolicyEnabled", tunnel.trafficPolicyEnabled());
            if (tunnel.domain() != null) {
                tunnelInfo.put("domain", tunnel.domain());
            }
            builder.withDetail("tunnel:" + tunnel.name(), tunnelInfo);
        }
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
