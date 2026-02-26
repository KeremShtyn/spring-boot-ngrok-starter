package com.kermel.ngrok.autoconfigure;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.protocol.Tunnel;
import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.event.NgrokTunnelClosedEvent;
import com.kermel.ngrok.event.NgrokTunnelEstablishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Monitors active ngrok tunnels and automatically reconnects dropped tunnels.
 *
 * <p>Periodically checks if tunnels listed in the registry are still alive
 * by querying the ngrok client. If a tunnel has dropped, attempts to
 * re-establish it with exponential backoff.
 */
public class NgrokTunnelReconnector {

    private static final Logger log = LoggerFactory.getLogger(NgrokTunnelReconnector.class);

    private final NgrokClient ngrokClient;
    private final NgrokTunnelManager tunnelManager;
    private final NgrokTunnelRegistry tunnelRegistry;
    private final NgrokProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    /** Stores the original config for each tunnel name so we can recreate them. */
    private final Map<String, NgrokProperties.TunnelProperties> tunnelConfigs;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> checkTask;

    private final AtomicInteger totalReconnections = new AtomicInteger(0);
    private final AtomicInteger failedReconnections = new AtomicInteger(0);
    private volatile boolean enabled;

    public NgrokTunnelReconnector(NgrokClient ngrokClient,
                                  NgrokTunnelManager tunnelManager,
                                  NgrokTunnelRegistry tunnelRegistry,
                                  NgrokProperties properties,
                                  ApplicationEventPublisher eventPublisher) {
        this.ngrokClient = ngrokClient;
        this.tunnelManager = tunnelManager;
        this.tunnelRegistry = tunnelRegistry;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
        this.tunnelConfigs = new java.util.concurrent.ConcurrentHashMap<>();
        this.enabled = properties.getReconnection().isEnabled();
    }

    /**
     * Register the original configuration for a tunnel so it can be recreated.
     */
    public void registerTunnelConfig(String name, NgrokProperties.TunnelProperties config) {
        tunnelConfigs.put(name, config);
    }

    /**
     * Start the periodic tunnel health check.
     */
    public void start() {
        if (!enabled) {
            log.debug("Tunnel reconnection is disabled");
            return;
        }

        NgrokProperties.ReconnectionProperties reconnection = properties.getReconnection();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ngrok-reconnector");
            t.setDaemon(true);
            return t;
        });

        checkTask = scheduler.scheduleWithFixedDelay(
                this::checkTunnels,
                reconnection.getCheckIntervalSeconds(),
                reconnection.getCheckIntervalSeconds(),
                TimeUnit.SECONDS
        );

        log.debug("Tunnel reconnector started with {}s check interval", reconnection.getCheckIntervalSeconds());
    }

    /**
     * Stop the reconnector and release resources.
     */
    public void stop() {
        if (checkTask != null) {
            checkTask.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        tunnelConfigs.clear();
        log.debug("Tunnel reconnector stopped");
    }

    /**
     * Check all registered tunnels and reconnect any that have dropped.
     */
    void checkTunnels() {
        if (tunnelRegistry.isEmpty()) {
            return;
        }

        try {
            // Get the list of currently active tunnels from ngrok
            List<Tunnel> activeTunnels = ngrokClient.getTunnels();
            Set<String> activeUrls = activeTunnels.stream()
                    .map(Tunnel::getPublicUrl)
                    .collect(Collectors.toSet());

            // Check each registered tunnel
            Set<String> tunnelNames = new HashSet<>(tunnelRegistry.getAllTunnels().stream()
                    .map(NgrokTunnel::name)
                    .toList());

            for (String name : tunnelNames) {
                NgrokTunnel registered = tunnelRegistry.getTunnel(name).orElse(null);
                if (registered == null) {
                    continue;
                }

                if (!activeUrls.contains(registered.publicUrl())) {
                    log.warn("Tunnel '{}' ({}) appears to have dropped — attempting reconnection",
                            name, registered.publicUrl());
                    reconnectTunnel(name, registered);
                }
            }
        } catch (Exception e) {
            log.debug("Error during tunnel health check: {}", e.getMessage());
        }
    }

    private void reconnectTunnel(String name, NgrokTunnel droppedTunnel) {
        NgrokProperties.TunnelProperties config = tunnelConfigs.get(name);
        if (config == null) {
            log.warn("No configuration found for tunnel '{}' — cannot reconnect", name);
            failedReconnections.incrementAndGet();
            return;
        }

        // Publish close event for the dropped tunnel
        eventPublisher.publishEvent(new NgrokTunnelClosedEvent(this, droppedTunnel));
        tunnelRegistry.deregister(name);

        NgrokProperties.ReconnectionProperties reconnection = properties.getReconnection();
        int maxAttempts = reconnection.getMaxAttempts();
        double delay = reconnection.getInitialDelaySeconds();

        for (int attempt = 1; maxAttempts == 0 || attempt <= maxAttempts; attempt++) {
            try {
                log.info("Reconnecting tunnel '{}' (attempt {}/{})", name, attempt,
                        maxAttempts == 0 ? "unlimited" : maxAttempts);

                NgrokTunnel newTunnel = tunnelManager.createTunnel(name, config);
                tunnelRegistry.register(newTunnel);
                eventPublisher.publishEvent(new NgrokTunnelEstablishedEvent(this, newTunnel));
                totalReconnections.incrementAndGet();

                log.info("Tunnel '{}' reconnected successfully: {}", name, newTunnel.publicUrl());
                return;
            } catch (Exception e) {
                log.warn("Reconnection attempt {} for tunnel '{}' failed: {}", attempt, name, e.getMessage());

                if (maxAttempts != 0 && attempt >= maxAttempts) {
                    log.error("Exhausted {} reconnection attempts for tunnel '{}'. Tunnel will remain disconnected.",
                            maxAttempts, name);
                    failedReconnections.incrementAndGet();
                    return;
                }

                // Exponential backoff
                try {
                    long sleepMs = (long) (delay * 1000);
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                delay = Math.min(delay * reconnection.getBackoffMultiplier(), reconnection.getMaxDelaySeconds());
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getTotalReconnections() {
        return totalReconnections.get();
    }

    public int getFailedReconnections() {
        return failedReconnections.get();
    }
}
