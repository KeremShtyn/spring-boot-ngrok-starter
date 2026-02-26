package com.kermel.ngrok.webhook.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebhookRegistrationResult")
class WebhookRegistrationResultTest {

    @Test
    void successFactory() {
        WebhookRegistrationResult result = WebhookRegistrationResult.success(
                "stripe", "we_123", "https://abc.ngrok.io/webhooks/stripe", true);

        assertThat(result.provider()).isEqualTo("stripe");
        assertThat(result.webhookId()).isEqualTo("we_123");
        assertThat(result.webhookUrl()).isEqualTo("https://abc.ngrok.io/webhooks/stripe");
        assertThat(result.success()).isTrue();
        assertThat(result.autoDeregister()).isTrue();
        assertThat(result.registeredAt()).isNotNull();
    }

    @Test
    void failureFactory() {
        WebhookRegistrationResult result = WebhookRegistrationResult.failure(
                "github", "https://abc.ngrok.io/webhooks/github", "401 Unauthorized");

        assertThat(result.provider()).isEqualTo("github");
        assertThat(result.webhookId()).isNull();
        assertThat(result.success()).isFalse();
        assertThat(result.autoDeregister()).isFalse();
        assertThat(result.message()).isEqualTo("401 Unauthorized");
    }

    @Test
    void logOnlyFactory() {
        WebhookRegistrationResult result = WebhookRegistrationResult.logOnly(
                "slack", "https://abc.ngrok.io/webhooks/slack");

        assertThat(result.provider()).isEqualTo("slack");
        assertThat(result.webhookId()).isNull();
        assertThat(result.success()).isTrue();
        assertThat(result.autoDeregister()).isFalse();
        assertThat(result.message()).contains("manually");
    }
}
