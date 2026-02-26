package com.kermel.ngrok.webhook.provider;

import com.kermel.ngrok.webhook.WebhookProperties;
import com.kermel.ngrok.webhook.WebhookRegistrationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("StripeWebhookProvider")
class StripeWebhookProviderTest {

    @Test
    void nameIsStripe() {
        StripeWebhookProvider provider = createProvider();
        assertThat(provider.name()).isEqualTo("stripe");
    }

    @Test
    void registerCreatesWebhookEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.stripe.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.stripe.com/v1/webhook_endpoints"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess("{\"id\": \"we_abc123\", \"url\": \"https://abc.ngrok.io/webhooks/stripe\"}",
                        MediaType.APPLICATION_JSON));

        WebhookProperties.StripeWebhookConfig config = new WebhookProperties.StripeWebhookConfig();
        config.setEnabled(true);
        config.setApiKey("sk_test_xxx");
        config.setEvents(List.of("payment_intent.succeeded"));

        StripeWebhookProvider provider = new StripeWebhookProvider(config, builder.build());
        WebhookRegistrationResult result = provider.register("https://abc.ngrok.io/webhooks/stripe");

        assertThat(result.success()).isTrue();
        assertThat(result.webhookId()).isEqualTo("we_abc123");
        assertThat(result.provider()).isEqualTo("stripe");
        server.verify();
    }

    @Test
    void registerHandlesApiError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.stripe.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.stripe.com/v1/webhook_endpoints"))
                .andRespond(withServerError());

        WebhookProperties.StripeWebhookConfig config = new WebhookProperties.StripeWebhookConfig();
        config.setEnabled(true);
        config.setApiKey("sk_test_bad");

        StripeWebhookProvider provider = new StripeWebhookProvider(config, builder.build());

        assertThatThrownBy(() -> provider.register("https://abc.ngrok.io/webhooks/stripe"))
                .isInstanceOf(WebhookRegistrationException.class)
                .hasMessageContaining("Stripe");
    }

    @Test
    void deregisterDeletesEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.stripe.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.stripe.com/v1/webhook_endpoints/we_abc123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"id\": \"we_abc123\", \"deleted\": true}", MediaType.APPLICATION_JSON));

        WebhookProperties.StripeWebhookConfig config = new WebhookProperties.StripeWebhookConfig();
        config.setEnabled(true);
        config.setApiKey("sk_test_xxx");

        StripeWebhookProvider provider = new StripeWebhookProvider(config, builder.build());
        WebhookRegistrationResult registration = WebhookRegistrationResult.success(
                "stripe", "we_abc123", "https://abc.ngrok.io/webhooks/stripe", true);

        provider.deregister(registration);
        server.verify();
    }

    @Test
    void deregisterSkippedWhenNoWebhookId() {
        StripeWebhookProvider provider = createProvider();
        WebhookRegistrationResult registration = WebhookRegistrationResult.failure(
                "stripe", "https://abc.ngrok.io/webhooks/stripe", "failed");

        // Should not throw — no webhook ID to deregister
        provider.deregister(registration);
    }

    @Test
    void pathFromConfig() {
        WebhookProperties.StripeWebhookConfig config = new WebhookProperties.StripeWebhookConfig();
        config.setPath("/api/stripe-hook");
        StripeWebhookProvider provider = new StripeWebhookProvider(config, RestClient.create());
        assertThat(provider.path()).isEqualTo("/api/stripe-hook");
    }

    @Test
    void autoDeregisterFromConfig() {
        WebhookProperties.StripeWebhookConfig config = new WebhookProperties.StripeWebhookConfig();
        config.setAutoDeregister(false);
        // Provider doesn't expose this directly — it's used during register()
        assertThat(config.isAutoDeregister()).isFalse();
    }

    private StripeWebhookProvider createProvider() {
        WebhookProperties.StripeWebhookConfig config = new WebhookProperties.StripeWebhookConfig();
        config.setEnabled(true);
        config.setApiKey("sk_test_xxx");
        return new StripeWebhookProvider(config, RestClient.create());
    }
}
