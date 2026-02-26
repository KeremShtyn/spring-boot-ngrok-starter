package com.kermel.ngrok.webhook.provider;

import com.kermel.ngrok.webhook.WebhookRegistrationException;

/**
 * Interface for webhook registration providers.
 *
 * <p>Implementations handle registering and deregistering webhook URLs
 * with external services (Stripe, GitHub, Slack, Twilio, etc.).
 */
public interface WebhookProvider {

    /**
     * Human-readable name of this provider (e.g., "stripe", "github").
     */
    String name();

    /**
     * Check if this provider is enabled in configuration.
     */
    boolean isEnabled();

    /**
     * The path suffix appended to the ngrok public URL to form the full webhook URL.
     * For example, "/webhooks/stripe" results in "https://abc.ngrok.io/webhooks/stripe".
     */
    String path();

    /**
     * Optional tunnel name this provider targets. Empty string means the default tunnel.
     */
    default String tunnel() {
        return "";
    }

    /**
     * Register the webhook URL with the external service.
     *
     * @param webhookUrl the full webhook URL (ngrok public URL + path)
     * @return the registration result
     * @throws WebhookRegistrationException if registration fails
     */
    WebhookRegistrationResult register(String webhookUrl) throws WebhookRegistrationException;

    /**
     * Deregister a previously registered webhook.
     *
     * @param registration the result from a previous {@link #register} call
     * @throws WebhookRegistrationException if deregistration fails
     */
    void deregister(WebhookRegistrationResult registration) throws WebhookRegistrationException;
}
