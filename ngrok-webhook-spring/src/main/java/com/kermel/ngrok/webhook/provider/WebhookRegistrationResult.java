package com.kermel.ngrok.webhook.provider;

import java.time.Instant;

/**
 * Result of a webhook registration attempt.
 *
 * @param provider       provider name (e.g., "stripe", "github")
 * @param webhookId      ID returned by the provider (used for deregistration), or null
 * @param webhookUrl     full webhook URL that was registered
 * @param success        whether the registration succeeded
 * @param message        human-readable status message or error description
 * @param autoDeregister whether this webhook should be deregistered on shutdown
 * @param registeredAt   timestamp of the registration attempt
 */
public record WebhookRegistrationResult(
        String provider,
        String webhookId,
        String webhookUrl,
        boolean success,
        String message,
        boolean autoDeregister,
        Instant registeredAt
) {

    /**
     * Create a successful registration result.
     */
    public static WebhookRegistrationResult success(String provider, String webhookId,
                                                     String webhookUrl, boolean autoDeregister) {
        return new WebhookRegistrationResult(
                provider, webhookId, webhookUrl, true,
                "Webhook registered successfully", autoDeregister, Instant.now());
    }

    /**
     * Create a failed registration result.
     */
    public static WebhookRegistrationResult failure(String provider, String webhookUrl, String message) {
        return new WebhookRegistrationResult(
                provider, null, webhookUrl, false, message, false, Instant.now());
    }

    /**
     * Create a log-only result (for providers like Slack that don't support programmatic registration).
     */
    public static WebhookRegistrationResult logOnly(String provider, String webhookUrl) {
        return new WebhookRegistrationResult(
                provider, null, webhookUrl, true,
                "URL logged — configure manually in provider dashboard", false, Instant.now());
    }
}
