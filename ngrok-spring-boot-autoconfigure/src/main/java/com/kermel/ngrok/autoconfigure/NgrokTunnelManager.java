package com.kermel.ngrok.autoconfigure;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.protocol.CreateTunnel;
import com.github.alexdlaird.ngrok.protocol.Proto;
import com.github.alexdlaird.ngrok.protocol.Tunnel;
import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.exception.NgrokBinaryException;
import com.kermel.ngrok.exception.NgrokPortConflictException;
import com.kermel.ngrok.exception.NgrokTunnelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Manages ngrok tunnel creation and teardown.
 *
 * <p>Handles protocol selection, domain binding, traffic policy attachment,
 * basic auth configuration, and retry logic for each tunnel.
 */
public class NgrokTunnelManager {

    private static final Logger log = LoggerFactory.getLogger(NgrokTunnelManager.class);

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long DEFAULT_RETRY_DELAY_MS = 1000;

    private final NgrokClient ngrokClient;
    private final NgrokProperties properties;
    private final ResourceLoader resourceLoader;

    public NgrokTunnelManager(NgrokClient ngrokClient, NgrokProperties properties) {
        this.ngrokClient = ngrokClient;
        this.properties = properties;
        this.resourceLoader = new DefaultResourceLoader();
    }

    /**
     * Create a tunnel with the given name and configuration.
     * Includes retry logic with default attempts and delay.
     *
     * @param name   tunnel name for identification
     * @param config tunnel properties
     * @return an {@link NgrokTunnel} representing the established tunnel
     */
    public NgrokTunnel createTunnel(String name, NgrokProperties.TunnelProperties config) {
        return createTunnelWithRetry(name, config, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_DELAY_MS);
    }

    /**
     * Create a tunnel with explicit retry parameters.
     *
     * @param name       tunnel name
     * @param config     tunnel properties
     * @param maxRetries maximum retry attempts (0 = no retries)
     * @param delayMs    delay between retries in milliseconds
     * @return an {@link NgrokTunnel} representing the established tunnel
     */
    public NgrokTunnel createTunnelWithRetry(String name, NgrokProperties.TunnelProperties config,
                                              int maxRetries, long delayMs) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log.info("Retrying tunnel '{}' creation (attempt {}/{})", name, attempt, maxRetries);
                    Thread.sleep(delayMs);
                }
                return doCreateTunnel(name, config);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NgrokTunnelException("Tunnel creation interrupted for '" + name + "'", e);
            } catch (Exception e) {
                lastException = e;
                NgrokTunnelException classified = classifyException(name, config, e);

                // Don't retry non-transient errors
                if (classified instanceof NgrokPortConflictException
                        || classified instanceof NgrokBinaryException) {
                    throw classified;
                }

                if (attempt < maxRetries) {
                    log.warn("Tunnel '{}' creation failed (attempt {}/{}): {}",
                            name, attempt + 1, maxRetries + 1, e.getMessage());
                }
            }
        }

        throw classifyException(name, config, lastException);
    }

    /**
     * Disconnect all tunnels and kill the ngrok process.
     */
    public void shutdown() {
        try {
            log.debug("Shutting down ngrok tunnels");
            ngrokClient.kill();
            log.info("ngrok process terminated");
        } catch (Exception e) {
            log.warn("Error while shutting down ngrok: {}", e.getMessage());
        }
    }

    /**
     * Get the underlying NgrokClient (used by reconnector for tunnel health checks).
     */
    NgrokClient getNgrokClient() {
        return ngrokClient;
    }

    private NgrokTunnel doCreateTunnel(String name, NgrokProperties.TunnelProperties config) {
        CreateTunnel.Builder builder = new CreateTunnel.Builder()
                .withName(name)
                .withAddr(config.getPort());

        switch (config.getProtocol()) {
            case "tcp" -> builder.withProto(Proto.TCP);
            case "tls" -> builder.withProto(Proto.TLS);
            default -> builder.withBindTls(config.isHttpsOnly());
        }

        if (config.getDomain() != null) {
            builder.withDomain(config.getDomain());
        }

        String policy = resolveTrafficPolicy(name, config);
        if (policy != null) {
            builder.withTrafficPolicy(policy);
        }

        if (config.getBasicAuth() != null) {
            builder.withAuth(config.getBasicAuth());
        }

        log.debug("Creating ngrok tunnel '{}' on port {} with protocol {}",
                name, config.getPort(), config.getProtocol());

        Tunnel tunnel = ngrokClient.connect(builder.build());

        log.info("ngrok tunnel '{}' established: {} -> localhost:{}",
                name, tunnel.getPublicUrl(), config.getPort());

        return new NgrokTunnel(
                name,
                tunnel.getPublicUrl(),
                config.getPort(),
                config.getProtocol(),
                Instant.now(),
                config.getDomain(),
                policy != null
        );
    }

    /**
     * Classify an exception into a more specific type based on the error message.
     */
    private NgrokTunnelException classifyException(String name, NgrokProperties.TunnelProperties config, Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        if (message.contains("address already in use") || message.contains("port is already")) {
            return new NgrokPortConflictException(
                    config.getPort() != null ? config.getPort() : properties.getInspection().getPort(), e);
        }

        if (message.contains("ngrok not found") || message.contains("no such file")
                || message.contains("cannot find") || message.contains("binary")) {
            return new NgrokBinaryException(
                    "ngrok binary not found. Ensure ngrok is installed or let java-ngrok download it automatically. " +
                            "You can also set ngrok.binary-path in application.yml. Error: " + e.getMessage(), e);
        }

        return new NgrokTunnelException(
                "Failed to create ngrok tunnel '" + name + "' on port " + config.getPort() + ": " + e.getMessage(), e);
    }

    /**
     * Resolve the traffic policy YAML from inline config, file reference, or return null.
     */
    private String resolveTrafficPolicy(String tunnelName, NgrokProperties.TunnelProperties config) {
        if (config.getTrafficPolicy() != null && !config.getTrafficPolicy().isBlank()) {
            return config.getTrafficPolicy();
        }

        if (config.getTrafficPolicyFile() != null && !config.getTrafficPolicyFile().isBlank()) {
            return loadTrafficPolicyFile(config.getTrafficPolicyFile());
        }

        return null;
    }

    private String loadTrafficPolicyFile(String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            try (InputStream is = resource.getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new NgrokTunnelException("Failed to load traffic policy file: " + location, e);
        }
    }
}
