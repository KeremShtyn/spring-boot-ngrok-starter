package com.kermel.ngrok.core;

/**
 * Provides convenient access to the ngrok public URL.
 *
 * <p>Inject this bean to access the public URL of the default tunnel:
 * <pre>{@code
 * @Autowired
 * private NgrokPublicUrlProvider publicUrlProvider;
 *
 * String url = publicUrlProvider.getPublicUrl();
 * }</pre>
 *
 * <p>Note: The URL is only available after the tunnel is established.
 * For construction-time access, listen to {@link com.kermel.ngrok.event.NgrokReadyEvent}:
 * <pre>{@code
 * @EventListener
 * public void onNgrokReady(NgrokReadyEvent event) {
 *     String url = event.getTunnels().iterator().next().publicUrl();
 * }
 * }</pre>
 */
public class NgrokPublicUrlProvider {

    private final NgrokTunnelRegistry tunnelRegistry;

    public NgrokPublicUrlProvider(NgrokTunnelRegistry tunnelRegistry) {
        this.tunnelRegistry = tunnelRegistry;
    }

    /**
     * Get the public URL of the default tunnel.
     *
     * @return the public URL, or null if no tunnels are active yet
     */
    public String getPublicUrl() {
        return tunnelRegistry.getPublicUrl();
    }

    /**
     * Get the public URL of a named tunnel.
     *
     * @param tunnelName the tunnel name
     * @return the public URL, or null if the tunnel is not found
     */
    public String getPublicUrl(String tunnelName) {
        return tunnelRegistry.getPublicUrl(tunnelName);
    }

    /**
     * Check if any tunnel is currently active.
     */
    public boolean isAvailable() {
        return !tunnelRegistry.isEmpty();
    }
}
