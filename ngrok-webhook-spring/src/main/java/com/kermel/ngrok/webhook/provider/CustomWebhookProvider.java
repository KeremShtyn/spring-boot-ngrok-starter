package com.kermel.ngrok.webhook.provider;

import com.kermel.ngrok.webhook.WebhookProperties;
import com.kermel.ngrok.webhook.WebhookRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Generic webhook provider for custom HTTP-based services.
 *
 * <p>Supports template-based registration using variable interpolation:
 * <ul>
 *   <li>{@code {{ngrok.public-url}}} — replaced with the ngrok public URL + path</li>
 *   <li>{@code {{webhook.id}}} — replaced with the webhook ID (for deregistration)</li>
 * </ul>
 *
 * <p>Example configuration:
 * <pre>{@code
 * ngrok.webhooks.custom:
 *   - name: my-service
 *     registration-url: https://api.example.com/webhooks
 *     method: POST
 *     headers:
 *       Authorization: "Bearer ${TOKEN}"
 *     body-template: |
 *       {"url": "{{ngrok.public-url}}", "events": ["order.created"]}
 * }</pre>
 */
public class CustomWebhookProvider implements WebhookProvider {

    private static final Logger log = LoggerFactory.getLogger(CustomWebhookProvider.class);

    private final WebhookProperties.CustomWebhookConfig config;
    private final RestClient restClient;

    public CustomWebhookProvider(WebhookProperties.CustomWebhookConfig config) {
        this.config = config;
        RestClient.Builder builder = RestClient.builder();
        if (config.getHeaders() != null) {
            config.getHeaders().forEach(builder::defaultHeader);
        }
        this.restClient = builder.build();
    }

    // Visible for testing
    CustomWebhookProvider(WebhookProperties.CustomWebhookConfig config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public String name() {
        return "custom:" + config.getName();
    }

    @Override
    public boolean isEnabled() {
        return config.getRegistrationUrl() != null && !config.getRegistrationUrl().isBlank();
    }

    @Override
    public String path() {
        return "";
    }

    @Override
    public String tunnel() {
        return config.getTunnel();
    }

    @Override
    @SuppressWarnings("unchecked")
    public WebhookRegistrationResult register(String webhookUrl) {
        try {
            String body = interpolate(config.getBodyTemplate(), webhookUrl, null);

            RestClient.RequestBodySpec request = restClient
                    .method(org.springframework.http.HttpMethod.valueOf(config.getMethod().toUpperCase()))
                    .uri(config.getRegistrationUrl())
                    .contentType(MediaType.APPLICATION_JSON);

            Map<String, Object> response;
            if (body != null && !body.isBlank()) {
                response = request.body(body).retrieve().body(Map.class);
            } else {
                response = request.retrieve().body(Map.class);
            }

            String webhookId = null;
            if (response != null) {
                // Try common ID field names
                Object id = response.get("id");
                if (id == null) id = response.get("webhook_id");
                if (id == null) id = response.get("hookId");
                if (id != null) webhookId = String.valueOf(id);
            }

            log.info("Custom webhook '{}' registered at {}", config.getName(), webhookUrl);
            return WebhookRegistrationResult.success(name(), webhookId, webhookUrl, config.isAutoDeregister());

        } catch (Exception e) {
            log.error("Failed to register custom webhook '{}': {}", config.getName(), e.getMessage());
            throw new WebhookRegistrationException(
                    "Custom webhook '" + config.getName() + "' registration failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deregister(WebhookRegistrationResult registration) {
        if (config.getDeregistrationUrl() == null || config.getDeregistrationUrl().isBlank()) {
            log.debug("No deregistration URL configured for custom webhook '{}'", config.getName());
            return;
        }

        try {
            String url = interpolate(config.getDeregistrationUrl(), registration.webhookUrl(), registration.webhookId());

            restClient
                    .method(org.springframework.http.HttpMethod.valueOf(config.getDeregistrationMethod().toUpperCase()))
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Custom webhook '{}' deregistered", config.getName());
        } catch (Exception e) {
            log.warn("Failed to deregister custom webhook '{}': {}", config.getName(), e.getMessage());
            throw new WebhookRegistrationException(
                    "Custom webhook '" + config.getName() + "' deregistration failed: " + e.getMessage(), e);
        }
    }

    private String interpolate(String template, String webhookUrl, String webhookId) {
        if (template == null) return null;
        String result = template.replace("{{ngrok.public-url}}", webhookUrl != null ? webhookUrl : "");
        result = result.replace("{{webhook.id}}", webhookId != null ? webhookId : "");
        return result;
    }
}
