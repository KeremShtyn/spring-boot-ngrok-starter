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

@DisplayName("GitHubWebhookProvider")
class GitHubWebhookProviderTest {

    @Test
    void nameIsGithub() {
        GitHubWebhookProvider provider = createProvider();
        assertThat(provider.name()).isEqualTo("github");
    }

    @Test
    void registerCreatesRepoHook() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.github.com/repos/kermel/my-app/hooks"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"id\": 98765, \"active\": true}",
                        MediaType.APPLICATION_JSON));

        WebhookProperties.GitHubWebhookConfig config = githubConfig();
        GitHubWebhookProvider provider = new GitHubWebhookProvider(config, builder.build());

        WebhookRegistrationResult result = provider.register("https://abc.ngrok.io/webhooks/github");

        assertThat(result.success()).isTrue();
        assertThat(result.webhookId()).isEqualTo("98765");
        assertThat(result.provider()).isEqualTo("github");
        server.verify();
    }

    @Test
    void registerHandlesApiError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.github.com/repos/kermel/my-app/hooks"))
                .andRespond(withServerError());

        WebhookProperties.GitHubWebhookConfig config = githubConfig();
        GitHubWebhookProvider provider = new GitHubWebhookProvider(config, builder.build());

        assertThatThrownBy(() -> provider.register("https://abc.ngrok.io/webhooks/github"))
                .isInstanceOf(WebhookRegistrationException.class)
                .hasMessageContaining("GitHub");
    }

    @Test
    void deregisterDeletesHook() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo("https://api.github.com/repos/kermel/my-app/hooks/98765"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess());

        WebhookProperties.GitHubWebhookConfig config = githubConfig();
        GitHubWebhookProvider provider = new GitHubWebhookProvider(config, builder.build());

        WebhookRegistrationResult registration = WebhookRegistrationResult.success(
                "github", "98765", "https://abc.ngrok.io/webhooks/github", true);

        provider.deregister(registration);
        server.verify();
    }

    @Test
    void deregisterSkippedWhenNoHookId() {
        GitHubWebhookProvider provider = createProvider();
        WebhookRegistrationResult registration = WebhookRegistrationResult.failure(
                "github", "https://abc.ngrok.io/webhooks/github", "failed");

        // No hook ID — should not throw
        provider.deregister(registration);
    }

    @Test
    void pathFromConfig() {
        WebhookProperties.GitHubWebhookConfig config = githubConfig();
        config.setPath("/api/gh-hook");
        GitHubWebhookProvider provider = new GitHubWebhookProvider(config, RestClient.create());
        assertThat(provider.path()).isEqualTo("/api/gh-hook");
    }

    @Test
    void contentTypeFromConfig() {
        WebhookProperties.GitHubWebhookConfig config = githubConfig();
        assertThat(config.getContentType()).isEqualTo("json");

        config.setContentType("form");
        assertThat(config.getContentType()).isEqualTo("form");
    }

    private GitHubWebhookProvider createProvider() {
        return new GitHubWebhookProvider(githubConfig(), RestClient.create());
    }

    private WebhookProperties.GitHubWebhookConfig githubConfig() {
        WebhookProperties.GitHubWebhookConfig config = new WebhookProperties.GitHubWebhookConfig();
        config.setEnabled(true);
        config.setToken("ghp_test123");
        config.setOwner("kermel");
        config.setRepo("my-app");
        config.setEvents(List.of("push", "pull_request"));
        return config;
    }
}
