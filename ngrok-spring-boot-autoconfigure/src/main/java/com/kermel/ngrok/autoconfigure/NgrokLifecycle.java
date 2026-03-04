package com.kermel.ngrok.autoconfigure;

import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.event.NgrokReadyEvent;
import com.kermel.ngrok.event.NgrokTunnelClosedEvent;
import com.kermel.ngrok.event.NgrokTunnelEstablishedEvent;
import com.kermel.ngrok.exception.NgrokStartupException;
import com.kermel.ngrok.exception.NgrokTunnelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.SmartLifecycle;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the ngrok tunnel lifecycle within the Spring application.
 *
 * <p>Listens for {@link WebServerInitializedEvent} to detect the server port,
 * then creates tunnels and publishes Spring events. On shutdown, tunnels are
 * disconnected, the reconnector is stopped, and the ngrok process is killed.
 */
public class NgrokLifecycle implements SmartLifecycle, ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(NgrokLifecycle.class);

    private final NgrokTunnelManager tunnelManager;
    private final NgrokTunnelRegistry tunnelRegistry;
    private final NgrokProperties properties;
    private final NgrokBannerPrinter bannerPrinter;
    private final ApplicationEventPublisher eventPublisher;
    private final NgrokTunnelReconnector reconnector;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile int serverPort = -1;

    public NgrokLifecycle(NgrokTunnelManager tunnelManager,
                          NgrokTunnelRegistry tunnelRegistry,
                          NgrokProperties properties,
                          NgrokBannerPrinter bannerPrinter,
                          ApplicationEventPublisher eventPublisher,
                          NgrokTunnelReconnector reconnector) {
        this.tunnelManager = tunnelManager;
        this.tunnelRegistry = tunnelRegistry;
        this.properties = properties;
        this.bannerPrinter = bannerPrinter;
        this.eventPublisher = eventPublisher;
        this.reconnector = reconnector;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.serverPort = event.getWebServer().getPort();
        log.debug("Web server initialized on port {}", serverPort);
        start();
    }

    @Override
    public void start() {
        if (serverPort < 0 || !running.compareAndSet(false, true)) {
            return;
        }

        try {
            Map<String, NgrokProperties.TunnelProperties> tunnelConfigs = properties.getTunnels();

            if (tunnelConfigs.isEmpty()) {
                createSingleTunnel();
            } else {
                createMultipleTunnels(tunnelConfigs);
            }

            if (!tunnelRegistry.isEmpty()) {
                // Print banner
                bannerPrinter.print(tunnelRegistry.getAllTunnels());

                // Publish the NgrokReadyEvent before starting reconnector to avoid
                // race where health-check fires before listeners receive the ready event
                eventPublisher.publishEvent(new NgrokReadyEvent(this, tunnelRegistry.getAllTunnels()));

                // Start reconnector after event publication
                reconnector.start();
            } else {
                log.warn("No ngrok tunnels were created successfully");
            }
        } catch (Exception e) {
            // Reset running flag so start() can be retried after any failure
            running.set(false);
            throw e;
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        log.info("Stopping ngrok tunnels");

        // Stop the reconnector first
        reconnector.stop();

        // Publish close events
        for (NgrokTunnel tunnel : tunnelRegistry.getAllTunnels()) {
            try {
                eventPublisher.publishEvent(new NgrokTunnelClosedEvent(this, tunnel));
            } catch (Exception e) {
                log.debug("Error publishing tunnel closed event for '{}': {}", tunnel.name(), e.getMessage());
            }
        }

        tunnelRegistry.clear();
        tunnelManager.shutdown();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // Start after all other SmartLifecycle beans, stop before them
        return Integer.MAX_VALUE - 1;
    }

    @Override
    public boolean isAutoStartup() {
        // We start manually after receiving WebServerInitializedEvent
        return false;
    }

    private void createSingleTunnel() {
        NgrokProperties.TunnelProperties config = properties.getDefaultTunnel();
        if (config.getPort() == null) {
            config.setPort(serverPort);
        }

        try {
            NgrokTunnel tunnel = tunnelManager.createTunnel("default", config);
            tunnelRegistry.register(tunnel);
            reconnector.registerTunnelConfig("default", config);
            eventPublisher.publishEvent(new NgrokTunnelEstablishedEvent(this, tunnel));
        } catch (NgrokTunnelException e) {
            handleTunnelFailure("default", config, e);
        }
    }

    private void createMultipleTunnels(Map<String, NgrokProperties.TunnelProperties> tunnelConfigs) {
        for (Map.Entry<String, NgrokProperties.TunnelProperties> entry : tunnelConfigs.entrySet()) {
            String name = entry.getKey();
            NgrokProperties.TunnelProperties config = entry.getValue();

            if (config.getPort() == null) {
                config.setPort(serverPort);
            }

            try {
                NgrokTunnel tunnel = tunnelManager.createTunnel(name, config);
                tunnelRegistry.register(tunnel);
                reconnector.registerTunnelConfig(name, config);
                eventPublisher.publishEvent(new NgrokTunnelEstablishedEvent(this, tunnel));
            } catch (NgrokTunnelException e) {
                handleTunnelFailure(name, config, e);
            }
        }
    }

    private void handleTunnelFailure(String name, NgrokProperties.TunnelProperties config, NgrokTunnelException e) {
        if (config.isFailOpen()) {
            log.warn("Failed to create ngrok tunnel '{}', but fail-open is enabled — continuing without tunnel: {}",
                    name, e.getMessage());
        } else {
            throw e;
        }
    }
}
