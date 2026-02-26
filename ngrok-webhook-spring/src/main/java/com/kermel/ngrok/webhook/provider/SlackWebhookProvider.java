package com.kermel.ngrok.webhook.provider;

import com.kermel.ngrok.webhook.WebhookProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Webhook provider for Slack.
 *
 * <p>Slack does not support programmatic webhook URL updates for Event Subscriptions.
 * This provider operates in <strong>log-only</strong> mode: it prints the webhook URL
 * to the console so the developer can manually configure it in the Slack App dashboard.
 */
public class SlackWebhookProvider implements WebhookProvider {

    private static final Logger log = LoggerFactory.getLogger(SlackWebhookProvider.class);

    private final WebhookProperties.SlackWebhookConfig config;

    public SlackWebhookProvider(WebhookProperties.SlackWebhookConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "slack";
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
    public WebhookRegistrationResult register(String webhookUrl) {
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║  Slack Webhook URL (configure manually in Slack dashboard): ║");
        log.info("║  {}",  webhookUrl);
        log.info("╚══════════════════════════════════════════════════════════════╝");

        return WebhookRegistrationResult.logOnly("slack", webhookUrl);
    }

    @Override
    public void deregister(WebhookRegistrationResult registration) {
        // No-op — Slack webhooks are configured manually
        log.debug("Slack webhook deregistration is a no-op (manual configuration required)");
    }
}
