package com.kermel.ngrok.webhook;

import com.kermel.ngrok.core.NgrokTunnel;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.event.NgrokReadyEvent;
import com.kermel.ngrok.webhook.provider.WebhookProvider;
import com.kermel.ngrok.webhook.provider.WebhookRegistrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WebhookAutoRegistrar")
class WebhookAutoRegistrarTest {

    private NgrokTunnelRegistry tunnelRegistry;

    @BeforeEach
    void setUp() {
        tunnelRegistry = new NgrokTunnelRegistry();
        tunnelRegistry.register(new NgrokTunnel("default", "https://abc.ngrok-free.app", 8080, "http"));
    }

    @Test
    void registersEnabledProviders() {
        TestProvider provider = new TestProvider("test", true, "/webhooks/test");
        WebhookAutoRegistrar registrar = new WebhookAutoRegistrar(List.of(provider), tunnelRegistry);

        registrar.onNgrokReady(readyEvent());

        assertThat(registrar.getRegistrations()).hasSize(1);
        assertThat(registrar.getRegistrations().get(0).success()).isTrue();
        assertThat(registrar.getRegistrations().get(0).webhookUrl()).isEqualTo("https://abc.ngrok-free.app/webhooks/test");
    }

    @Test
    void skipsDisabledProviders() {
        TestProvider enabled = new TestProvider("enabled", true, "/a");
        TestProvider disabled = new TestProvider("disabled", false, "/b");
        WebhookAutoRegistrar registrar = new WebhookAutoRegistrar(List.of(enabled, disabled), tunnelRegistry);

        registrar.onNgrokReady(readyEvent());

        assertThat(registrar.getRegistrations()).hasSize(1);
        assertThat(registrar.getRegistrations().get(0).provider()).isEqualTo("enabled");
    }

    @Test
    void multipleProvidersAllRegistered() {
        TestProvider p1 = new TestProvider("stripe", true, "/webhooks/stripe");
        TestProvider p2 = new TestProvider("github", true, "/webhooks/github");
        TestProvider p3 = new TestProvider("slack", true, "/webhooks/slack");
        WebhookAutoRegistrar registrar = new WebhookAutoRegistrar(List.of(p1, p2, p3), tunnelRegistry);

        registrar.onNgrokReady(readyEvent());

        assertThat(registrar.getRegistrations()).hasSize(3);
    }

    @Test
    void failedProviderDoesNotBlockOthers() {
        TestProvider ok = new TestProvider("ok", true, "/a");
        FailingProvider failing = new FailingProvider("bad");
        TestProvider ok2 = new TestProvider("ok2", true, "/c");
        WebhookAutoRegistrar registrar = new WebhookAutoRegistrar(List.of(ok, failing, ok2), tunnelRegistry);

        registrar.onNgrokReady(readyEvent());

        assertThat(registrar.getRegistrations()).hasSize(3);
        assertThat(registrar.getRegistrations().get(0).success()).isTrue();
        assertThat(registrar.getRegistrations().get(1).success()).isFalse();
        assertThat(registrar.getRegistrations().get(2).success()).isTrue();
    }

    @Test
    void noProviders_noRegistrations() {
        WebhookAutoRegistrar registrar = new WebhookAutoRegistrar(List.of(), tunnelRegistry);

        registrar.onNgrokReady(readyEvent());

        assertThat(registrar.getRegistrations()).isEmpty();
    }

    @Test
    void namedTunnelProvider() {
        tunnelRegistry.register(new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"));
        TestProvider provider = new TestProvider("test", true, "/webhook", "api");
        WebhookAutoRegistrar registrar = new WebhookAutoRegistrar(List.of(provider), tunnelRegistry);

        registrar.onNgrokReady(readyEvent());

        assertThat(registrar.getRegistrations()).hasSize(1);
        assertThat(registrar.getRegistrations().get(0).webhookUrl()).isEqualTo("https://api.ngrok-free.app/webhook");
    }

    @Test
    void noTunnelAvailable_skipsProvider() {
        TestProvider provider = new TestProvider("test", true, "/webhook", "nonexistent-tunnel");
        WebhookAutoRegistrar registrar = new WebhookAutoRegistrar(List.of(provider), tunnelRegistry);

        registrar.onNgrokReady(readyEvent());

        assertThat(registrar.getRegistrations()).isEmpty();
    }

    @Test
    void autoDeregisterRegistrations() {
        TestProvider autoP = new TestProvider("auto", true, "/a", "", true);
        TestProvider noAutoP = new TestProvider("manual", true, "/b", "", false);
        WebhookAutoRegistrar registrar = new WebhookAutoRegistrar(List.of(autoP, noAutoP), tunnelRegistry);

        registrar.onNgrokReady(readyEvent());

        assertThat(registrar.getAutoDeregisterRegistrations()).hasSize(1);
        assertThat(registrar.getAutoDeregisterRegistrations().get(0).provider()).isEqualTo("auto");
    }

    private NgrokReadyEvent readyEvent() {
        return new NgrokReadyEvent(this, tunnelRegistry.getAllTunnels());
    }

    // --- Test providers ---

    static class TestProvider implements WebhookProvider {
        private final String providerName;
        private final boolean enabled;
        private final String providerPath;
        private final String tunnelName;
        private final boolean autoDeregister;

        TestProvider(String name, boolean enabled, String path) {
            this(name, enabled, path, "", true);
        }

        TestProvider(String name, boolean enabled, String path, String tunnel) {
            this(name, enabled, path, tunnel, true);
        }

        TestProvider(String name, boolean enabled, String path, String tunnel, boolean autoDeregister) {
            this.providerName = name;
            this.enabled = enabled;
            this.providerPath = path;
            this.tunnelName = tunnel;
            this.autoDeregister = autoDeregister;
        }

        @Override public String name() { return providerName; }
        @Override public boolean isEnabled() { return enabled; }
        @Override public String path() { return providerPath; }
        @Override public String tunnel() { return tunnelName; }

        @Override
        public WebhookRegistrationResult register(String webhookUrl) {
            return WebhookRegistrationResult.success(providerName, "wh_123", webhookUrl, autoDeregister);
        }

        @Override
        public void deregister(WebhookRegistrationResult registration) {}
    }

    static class FailingProvider implements WebhookProvider {
        private final String providerName;

        FailingProvider(String name) { this.providerName = name; }

        @Override public String name() { return providerName; }
        @Override public boolean isEnabled() { return true; }
        @Override public String path() { return "/fail"; }

        @Override
        public WebhookRegistrationResult register(String webhookUrl) {
            throw new WebhookRegistrationException("Simulated failure");
        }

        @Override
        public void deregister(WebhookRegistrationResult registration) {}
    }
}
