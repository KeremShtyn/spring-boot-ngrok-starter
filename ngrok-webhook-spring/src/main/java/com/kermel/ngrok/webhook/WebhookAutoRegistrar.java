package com.kermel.ngrok.webhook;

import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.event.NgrokReadyEvent;
import com.kermel.ngrok.webhook.provider.WebhookProvider;
import com.kermel.ngrok.webhook.provider.WebhookRegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Automatically registers webhooks when ngrok tunnels become available.
 *
 * <p>Listens for {@link NgrokReadyEvent} and iterates through all enabled
 * {@link WebhookProvider} beans, registering each one with the appropriate
 * tunnel's public URL. Registration failures are logged as warnings and
 * do not prevent the application from starting.
 */
public class WebhookAutoRegistrar {

    private static final Logger log = LoggerFactory.getLogger(WebhookAutoRegistrar.class);

    private final List<WebhookProvider> providers;
    private final NgrokTunnelRegistry tunnelRegistry;
    private final List<WebhookRegistrationResult> registrations = Collections.synchronizedList(new ArrayList<>());

    public WebhookAutoRegistrar(List<WebhookProvider> providers, NgrokTunnelRegistry tunnelRegistry) {
        this.providers = providers;
        this.tunnelRegistry = tunnelRegistry;
    }

    @EventListener
    public void onNgrokReady(NgrokReadyEvent event) {
        if (providers.isEmpty()) {
            return;
        }

        log.info("Registering webhooks with {} provider(s)", providers.size());

        for (WebhookProvider provider : providers) {
            if (!provider.isEnabled()) {
                log.debug("Webhook provider '{}' is disabled — skipping", provider.name());
                continue;
            }

            String publicUrl = resolvePublicUrl(provider);
            if (publicUrl == null) {
                log.warn("No tunnel URL available for webhook provider '{}' — skipping", provider.name());
                continue;
            }

            String path = provider.path();
            String normalizedUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
            String webhookUrl;
            if (path == null || path.isBlank()) {
                // No path configured — use the base URL without appending a trailing slash
                webhookUrl = normalizedUrl;
            } else {
                String normalizedPath = path.startsWith("/") ? path : "/" + path;
                webhookUrl = normalizedUrl + normalizedPath;
            }

            try {
                WebhookRegistrationResult result = provider.register(webhookUrl);
                registrations.add(result);

                if (result.success()) {
                    log.info("Webhook '{}' registered: {}", provider.name(), result.webhookUrl());
                } else {
                    log.warn("Webhook '{}' registration reported failure: {}", provider.name(), result.message());
                }
            } catch (Exception e) {
                log.warn("Webhook '{}' registration failed: {}", provider.name(), e.getMessage());
                registrations.add(WebhookRegistrationResult.failure(provider.name(), webhookUrl, e.getMessage()));
            }
        }
    }

    /**
     * Get all registration results (successful and failed).
     *
     * @return a snapshot copy of the current registrations
     */
    public List<WebhookRegistrationResult> getRegistrations() {
        synchronized (registrations) {
            return List.copyOf(registrations);
        }
    }

    /**
     * Get only the successful registrations that have auto-deregister enabled.
     *
     * @return a snapshot copy of the matching registrations
     */
    public List<WebhookRegistrationResult> getAutoDeregisterRegistrations() {
        synchronized (registrations) {
            return registrations.stream()
                    .filter(WebhookRegistrationResult::success)
                    .filter(WebhookRegistrationResult::autoDeregister)
                    .toList();
        }
    }

    private String resolvePublicUrl(WebhookProvider provider) {
        String tunnel = provider.tunnel();
        if (tunnel != null && !tunnel.isEmpty()) {
            return tunnelRegistry.getPublicUrl(tunnel);
        }
        return tunnelRegistry.getPublicUrl();
    }
}
