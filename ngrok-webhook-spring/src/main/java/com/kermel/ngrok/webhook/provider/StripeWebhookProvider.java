package com.kermel.ngrok.webhook.provider;

import com.kermel.ngrok.webhook.WebhookProperties;
import com.kermel.ngrok.webhook.WebhookRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Webhook provider for Stripe.
 *
 * <p>Registers webhook endpoints using the Stripe REST API:
 * <ul>
 *   <li>Create: {@code POST /v1/webhook_endpoints}</li>
 *   <li>Delete: {@code DELETE /v1/webhook_endpoints/{id}}</li>
 * </ul>
 */
public class StripeWebhookProvider implements WebhookProvider {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookProvider.class);
    private static final String STRIPE_API_BASE = "https://api.stripe.com";

    private final WebhookProperties.StripeWebhookConfig config;
    private final RestClient restClient;

    public StripeWebhookProvider(WebhookProperties.StripeWebhookConfig config) {
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl(STRIPE_API_BASE)
                .defaultHeader("Authorization", "Bearer " + config.getApiKey())
                .build();
    }

    // Visible for testing
    StripeWebhookProvider(WebhookProperties.StripeWebhookConfig config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public String path() {
        return config.getPath();
    }

    @Override
    public String tunnel() {
        return config.getTunnel();
    }

    @Override
    @SuppressWarnings("unchecked")
    public WebhookRegistrationResult register(String webhookUrl) {
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("url", webhookUrl);

            List<String> events = config.getEvents();
            if (events != null && !events.isEmpty()) {
                for (int i = 0; i < events.size(); i++) {
                    formData.add("enabled_events[]", events.get(i));
                }
            } else {
                formData.add("enabled_events[]", "*");
            }

            Map<String, Object> response = restClient.post()
                    .uri("/v1/webhook_endpoints")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            String webhookId = response != null && response.get("id") != null
                    ? String.valueOf(response.get("id")) : null;

            log.info("Stripe webhook registered: {} (ID: {})", webhookUrl, webhookId);
            return WebhookRegistrationResult.success("stripe", webhookId, webhookUrl, config.isAutoDeregister());

        } catch (Exception e) {
            log.error("Failed to register Stripe webhook at {}: {}", webhookUrl, e.getMessage());
            throw new WebhookRegistrationException("Stripe webhook registration failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deregister(WebhookRegistrationResult registration) {
        if (registration.webhookId() == null) {
            log.debug("No webhook ID to deregister for Stripe");
            return;
        }

        try {
            restClient.delete()
                    .uri("/v1/webhook_endpoints/{id}", registration.webhookId())
                    .retrieve()
                    .toBodilessEntity();

            log.info("Stripe webhook deregistered: {}", registration.webhookId());
        } catch (Exception e) {
            log.warn("Failed to deregister Stripe webhook {}: {}", registration.webhookId(), e.getMessage());
            throw new WebhookRegistrationException("Stripe webhook deregistration failed: " + e.getMessage(), e);
        }
    }
}
