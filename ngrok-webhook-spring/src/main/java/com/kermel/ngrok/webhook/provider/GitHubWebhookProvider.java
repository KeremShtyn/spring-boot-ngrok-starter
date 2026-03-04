package com.kermel.ngrok.webhook.provider;

import com.kermel.ngrok.webhook.WebhookProperties;
import com.kermel.ngrok.webhook.WebhookRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Webhook provider for GitHub.
 *
 * <p>Registers repository webhooks using the GitHub REST API:
 * <ul>
 *   <li>Create: {@code POST /repos/{owner}/{repo}/hooks}</li>
 *   <li>Delete: {@code DELETE /repos/{owner}/{repo}/hooks/{hook_id}}</li>
 * </ul>
 */
public class GitHubWebhookProvider implements WebhookProvider {

    private static final Logger log = LoggerFactory.getLogger(GitHubWebhookProvider.class);
    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final WebhookProperties.GitHubWebhookConfig config;
    private final RestClient restClient;

    public GitHubWebhookProvider(WebhookProperties.GitHubWebhookConfig config) {
        this.config = config;
        if (config.getToken() == null || config.getToken().isBlank()) {
            throw new IllegalArgumentException(
                    "GitHub webhook token must be configured. Set ngrok.webhooks.github.token in your application properties.");
        }
        this.restClient = RestClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .defaultHeader("Authorization", "Bearer " + config.getToken())
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    // Visible for testing
    GitHubWebhookProvider(WebhookProperties.GitHubWebhookConfig config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public String name() {
        return "github";
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
            Map<String, Object> hookConfig = new LinkedHashMap<>();
            hookConfig.put("url", webhookUrl);
            hookConfig.put("content_type", config.getContentType());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "web");
            body.put("active", true);
            body.put("config", hookConfig);

            if (config.getEvents() != null && !config.getEvents().isEmpty()) {
                body.put("events", config.getEvents());
            }

            Map<String, Object> response = restClient.post()
                    .uri("/repos/{owner}/{repo}/hooks", config.getOwner(), config.getRepo())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String hookId = response != null && response.get("id") != null
                    ? String.valueOf(response.get("id")) : null;

            log.info("GitHub webhook registered for {}/{}: {} (ID: {})",
                    config.getOwner(), config.getRepo(), webhookUrl, hookId);
            return WebhookRegistrationResult.success("github", hookId, webhookUrl, config.isAutoDeregister());

        } catch (Exception e) {
            log.error("Failed to register GitHub webhook for {}/{}: {}",
                    config.getOwner(), config.getRepo(), e.getMessage());
            throw new WebhookRegistrationException("GitHub webhook registration failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deregister(WebhookRegistrationResult registration) {
        if (registration.webhookId() == null) {
            log.debug("No hook ID to deregister for GitHub");
            return;
        }

        try {
            restClient.delete()
                    .uri("/repos/{owner}/{repo}/hooks/{hookId}",
                            config.getOwner(), config.getRepo(), registration.webhookId())
                    .retrieve()
                    .toBodilessEntity();

            log.info("GitHub webhook deregistered for {}/{}: {}",
                    config.getOwner(), config.getRepo(), registration.webhookId());
        } catch (Exception e) {
            log.warn("Failed to deregister GitHub webhook {}: {}", registration.webhookId(), e.getMessage());
            throw new WebhookRegistrationException("GitHub webhook deregistration failed: " + e.getMessage(), e);
        }
    }
}
