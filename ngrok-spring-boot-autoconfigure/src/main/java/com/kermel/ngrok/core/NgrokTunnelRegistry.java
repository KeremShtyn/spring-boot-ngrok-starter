package com.kermel.ngrok.core;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of all active ngrok tunnels.
 *
 * <p>Use this bean to look up tunnel URLs in your application code:
 * <pre>{@code
 * @Autowired
 * private NgrokTunnelRegistry tunnelRegistry;
 *
 * String publicUrl = tunnelRegistry.getPublicUrl();
 * }</pre>
 */
public class NgrokTunnelRegistry {

    private static final String DEFAULT_TUNNEL_NAME = "default";

    private final ConcurrentHashMap<String, NgrokTunnel> tunnels = new ConcurrentHashMap<>();

    /**
     * Register a tunnel in the registry.
     */
    public void register(NgrokTunnel tunnel) {
        tunnels.put(tunnel.name(), tunnel);
    }

    /**
     * Remove a tunnel from the registry.
     */
    public void deregister(String name) {
        tunnels.remove(name);
    }

    /**
     * Get a tunnel by name.
     */
    public Optional<NgrokTunnel> getTunnel(String name) {
        return Optional.ofNullable(tunnels.get(name));
    }

    /**
     * Get the default tunnel (named "default").
     * If no tunnel named "default" exists, returns the first registered tunnel.
     */
    public NgrokTunnel getDefaultTunnel() {
        NgrokTunnel defaultTunnel = tunnels.get(DEFAULT_TUNNEL_NAME);
        if (defaultTunnel != null) {
            return defaultTunnel;
        }
        return tunnels.values().stream().findFirst().orElse(null);
    }

    /**
     * Get all active tunnels as a snapshot.
     *
     * <p>Returns a new list each time; modifications to the registry
     * after this call are not reflected in the returned collection.
     */
    public Collection<NgrokTunnel> getAllTunnels() {
        return List.copyOf(tunnels.values());
    }

    /**
     * Shortcut to get the default tunnel's public URL.
     *
     * @return the public URL, or null if no tunnels are active
     */
    public String getPublicUrl() {
        NgrokTunnel tunnel = getDefaultTunnel();
        return tunnel != null ? tunnel.publicUrl() : null;
    }

    /**
     * Get the public URL for a specific named tunnel.
     *
     * @return the public URL, or null if the tunnel is not found
     */
    public String getPublicUrl(String tunnelName) {
        NgrokTunnel tunnel = tunnels.get(tunnelName);
        return tunnel != null ? tunnel.publicUrl() : null;
    }

    /**
     * Check if any tunnels are active.
     */
    public boolean isEmpty() {
        return tunnels.isEmpty();
    }

    /**
     * Get the number of active tunnels.
     */
    public int size() {
        return tunnels.size();
    }

    /**
     * Clear all tunnels from the registry.
     */
    public void clear() {
        tunnels.clear();
    }
}
