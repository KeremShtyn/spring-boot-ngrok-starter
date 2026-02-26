package com.kermel.ngrok.core;

import java.time.Instant;

/**
 * Immutable representation of an active ngrok tunnel.
 *
 * @param name                 tunnel name (e.g., "default", "api", "frontend")
 * @param publicUrl            the public URL assigned by ngrok
 * @param localPort            the local port being forwarded
 * @param protocol             the tunnel protocol (http, tcp, tls)
 * @param createdAt            when the tunnel was established
 * @param domain               custom domain if configured, null otherwise
 * @param trafficPolicyEnabled whether a traffic policy is attached to this tunnel
 */
public record NgrokTunnel(
        String name,
        String publicUrl,
        int localPort,
        String protocol,
        Instant createdAt,
        String domain,
        boolean trafficPolicyEnabled
) {

    /**
     * Creates a tunnel with current timestamp and no custom domain or traffic policy.
     */
    public NgrokTunnel(String name, String publicUrl, int localPort, String protocol) {
        this(name, publicUrl, localPort, protocol, Instant.now(), null, false);
    }

    /**
     * Returns the forwarding description (e.g., "https://abc.ngrok-free.app -> localhost:8080").
     */
    public String forwardingDescription() {
        return publicUrl + " -> localhost:" + localPort;
    }
}
