package com.kermel.ngrok.webhook;

import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.webhook.lifecycle.WebhookDeregistrar;
import com.kermel.ngrok.webhook.provider.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebhookAutoConfiguration")
class WebhookAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebhookAutoConfiguration.class));

    @Test
    void noBeansWithoutNgrokTunnelRegistry() {
        contextRunner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(WebhookAutoRegistrar.class);
            assertThat(ctx).doesNotHaveBean(WebhookDeregistrar.class);
        });
    }

    @Test
    void registrarAndDeregistrarCreated() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(WebhookAutoRegistrar.class);
                    assertThat(ctx).hasSingleBean(WebhookDeregistrar.class);
                });
    }

    @Test
    void stripeProviderCreatedWhenEnabled() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withPropertyValues(
                        "ngrok.webhooks.stripe.enabled=true",
                        "ngrok.webhooks.stripe.api-key=sk_test_xxx")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(StripeWebhookProvider.class);
                });
    }

    @Test
    void stripeProviderNotCreatedWhenDisabled() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(StripeWebhookProvider.class);
                });
    }

    @Test
    void githubProviderCreatedWhenEnabled() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withPropertyValues(
                        "ngrok.webhooks.github.enabled=true",
                        "ngrok.webhooks.github.token=ghp_test",
                        "ngrok.webhooks.github.owner=kermel",
                        "ngrok.webhooks.github.repo=test")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(GitHubWebhookProvider.class);
                });
    }

    @Test
    void slackProviderCreatedWhenEnabled() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withPropertyValues("ngrok.webhooks.slack.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SlackWebhookProvider.class);
                });
    }

    @Test
    void twilioProviderCreatedWhenEnabled() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withPropertyValues(
                        "ngrok.webhooks.twilio.enabled=true",
                        "ngrok.webhooks.twilio.account-sid=AC123",
                        "ngrok.webhooks.twilio.auth-token=token",
                        "ngrok.webhooks.twilio.phone-number=+1234567890")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TwilioWebhookProvider.class);
                });
    }

    @Test
    void multipleProvidersEnabled() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withPropertyValues(
                        "ngrok.webhooks.stripe.enabled=true",
                        "ngrok.webhooks.stripe.api-key=sk_test_xxx",
                        "ngrok.webhooks.slack.enabled=true",
                        "ngrok.webhooks.github.enabled=true",
                        "ngrok.webhooks.github.token=ghp_test",
                        "ngrok.webhooks.github.owner=test",
                        "ngrok.webhooks.github.repo=test")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(StripeWebhookProvider.class);
                    assertThat(ctx).hasSingleBean(SlackWebhookProvider.class);
                    assertThat(ctx).hasSingleBean(GitHubWebhookProvider.class);
                });
    }

    @Test
    void customWebhookProvidersNotRegisteredByDefault() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(CustomWebhookProvider.class);
                });
    }

    @Test
    void webhookPropertiesBound() {
        contextRunner
                .withBean(NgrokTunnelRegistry.class)
                .withPropertyValues(
                        "ngrok.webhooks.stripe.enabled=true",
                        "ngrok.webhooks.stripe.api-key=sk_test_xxx",
                        "ngrok.webhooks.stripe.path=/api/stripe")
                .run(ctx -> {
                    WebhookProperties props = ctx.getBean(WebhookProperties.class);
                    assertThat(props.getStripe().isEnabled()).isTrue();
                    assertThat(props.getStripe().getApiKey()).isEqualTo("sk_test_xxx");
                    assertThat(props.getStripe().getPath()).isEqualTo("/api/stripe");
                });
    }
}
