package com.kermel.ngrok.webhook.provider;

import com.kermel.ngrok.webhook.WebhookProperties;
import com.kermel.ngrok.webhook.WebhookRegistrationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("CustomWebhookProvider")
class CustomWebhookProviderTest {

    @Test
    void nameIncludesConfigName() {
        WebhookProperties.CustomWebhookConfig config = new WebhookProperties.CustomWebhookConfig();
        config.setName("order-service");
        config.setRegistrationUrl("https://api.example.com/webhooks");
        CustomWebhookProvider provider = new CustomWebhookProvider(config, RestClient.create());
        assertThat(provider.name()).isEqualTo("custom:order-service");
    }

    @Test
    void isEnabledWhenRegistrationUrlConfigured() {
        WebhookProperties.CustomWebhookConfig config = new WebhookProperties.CustomWebhookConfig();
        config.setRegistrationUrl("https://api.example.com/webhooks");
        CustomWebhookProvider provider = new CustomWebhookProvider(config, RestClient.create());
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    void isDisabledWhenNoRegistrationUrl() {
        WebhookProperties.CustomWebhookConfig config = new WebhookProperties.CustomWebhookConfig();
        CustomWebhookProvider provider = new CustomWebhookProvider(config, RestClient.create());
        assertThat(provider.isEnabled()).isFalse();
    }

    @Test
    void registerInterpolatesTemplate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.example.com/webhooks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"url\": \"https://abc.ngrok.io/callback\"}"))
                .andRespond(withSuccess("{\"id\": \"wh_42\"}", MediaType.APPLICATION_JSON));

        WebhookProperties.CustomWebhookConfig config = new WebhookProperties.CustomWebhookConfig();
        config.setName("test");
        config.setRegistrationUrl("https://api.example.com/webhooks");
        config.setBodyTemplate("{\"url\": \"{{ngrok.public-url}}\"}");

        CustomWebhookProvider provider = new CustomWebhookProvider(config, builder.build());
        WebhookRegistrationResult result = provider.register("https://abc.ngrok.io/callback");

        assertThat(result.success()).isTrue();
        assertThat(result.webhookId()).isEqualTo("wh_42");
        server.verify();
    }

    @Test
    void registerHandlesServerError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.example.com/webhooks"))
                .andRespond(withServerError());

        WebhookProperties.CustomWebhookConfig config = new WebhookProperties.CustomWebhookConfig();
        config.setName("failing");
        config.setRegistrationUrl("https://api.example.com/webhooks");

        CustomWebhookProvider provider = new CustomWebhookProvider(config, builder.build());

        assertThatThrownBy(() -> provider.register("https://abc.ngrok.io/callback"))
                .isInstanceOf(WebhookRegistrationException.class)
                .hasMessageContaining("failing");
    }

    @Test
    void deregisterInterpolatesWebhookId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.example.com/webhooks/wh_42"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        WebhookProperties.CustomWebhookConfig config = new WebhookProperties.CustomWebhookConfig();
        config.setName("test");
        config.setRegistrationUrl("https://api.example.com/webhooks");
        config.setDeregistrationUrl("https://api.example.com/webhooks/{{webhook.id}}");
        config.setDeregistrationMethod("DELETE");

        CustomWebhookProvider provider = new CustomWebhookProvider(config, builder.build());
        WebhookRegistrationResult registration = WebhookRegistrationResult.success(
                "custom:test", "wh_42", "https://abc.ngrok.io/callback", true);

        provider.deregister(registration);
        server.verify();
    }

    @Test
    void deregisterNoOp_whenNoDeregistrationUrl() {
        WebhookProperties.CustomWebhookConfig config = new WebhookProperties.CustomWebhookConfig();
        config.setName("test");
        config.setRegistrationUrl("https://api.example.com/webhooks");
        // No deregistration URL set

        CustomWebhookProvider provider = new CustomWebhookProvider(config, RestClient.create());
        WebhookRegistrationResult registration = WebhookRegistrationResult.success(
                "custom:test", "wh_42", "https://abc.ngrok.io/callback", false);

        // Should not throw
        provider.deregister(registration);
    }

    @Test
    void customHeaders() {
        WebhookProperties.CustomWebhookConfig config = new WebhookProperties.CustomWebhookConfig();
        config.setName("auth-service");
        config.setRegistrationUrl("https://api.example.com/hooks");
        config.setHeaders(Map.of("Authorization", "Bearer token123"));

        CustomWebhookProvider provider = new CustomWebhookProvider(config);
        assertThat(provider.name()).isEqualTo("custom:auth-service");
    }
}
