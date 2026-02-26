package com.kermel.ngrok.webhook.provider;

import com.kermel.ngrok.webhook.WebhookProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("SlackWebhookProvider")
class SlackWebhookProviderTest {

    @Test
    void nameIsSlack() {
        SlackWebhookProvider provider = createProvider(true);
        assertThat(provider.name()).isEqualTo("slack");
    }

    @Test
    void registerReturnsLogOnlyResult() {
        SlackWebhookProvider provider = createProvider(true);

        WebhookRegistrationResult result = provider.register("https://abc.ngrok.io/webhooks/slack");

        assertThat(result.success()).isTrue();
        assertThat(result.autoDeregister()).isFalse();
        assertThat(result.webhookId()).isNull();
        assertThat(result.webhookUrl()).isEqualTo("https://abc.ngrok.io/webhooks/slack");
        assertThat(result.message()).contains("manually");
    }

    @Test
    void deregisterIsNoOp() {
        SlackWebhookProvider provider = createProvider(true);
        WebhookRegistrationResult result = WebhookRegistrationResult.logOnly("slack", "https://abc.ngrok.io/webhooks/slack");

        assertThatNoException().isThrownBy(() -> provider.deregister(result));
    }

    @Test
    void isEnabledReflectsConfig() {
        assertThat(createProvider(true).isEnabled()).isTrue();
        assertThat(createProvider(false).isEnabled()).isFalse();
    }

    @Test
    void pathFromConfig() {
        WebhookProperties.SlackWebhookConfig config = new WebhookProperties.SlackWebhookConfig();
        config.setPath("/custom/slack");
        SlackWebhookProvider provider = new SlackWebhookProvider(config);
        assertThat(provider.path()).isEqualTo("/custom/slack");
    }

    private SlackWebhookProvider createProvider(boolean enabled) {
        WebhookProperties.SlackWebhookConfig config = new WebhookProperties.SlackWebhookConfig();
        config.setEnabled(enabled);
        return new SlackWebhookProvider(config);
    }
}
