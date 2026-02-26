package com.kermel.ngrok.webhook.lifecycle;

import com.kermel.ngrok.webhook.WebhookAutoRegistrar;
import com.kermel.ngrok.webhook.provider.WebhookProvider;
import com.kermel.ngrok.webhook.provider.WebhookRegistrationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deregisters webhooks on application shutdown.
 *
 * <p>For each registration that has {@code autoDeregister=true},
 * calls the corresponding provider's {@link WebhookProvider#deregister} method.
 * Failures are logged but do not prevent shutdown.
 */
public class WebhookDeregistrar implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeregistrar.class);

    private final WebhookAutoRegistrar registrar;
    private final List<WebhookProvider> providers;

    public WebhookDeregistrar(WebhookAutoRegistrar registrar, List<WebhookProvider> providers) {
        this.registrar = registrar;
        this.providers = providers;
    }

    @Override
    public void destroy() {
        List<WebhookRegistrationResult> toDeregister = registrar.getAutoDeregisterRegistrations();
        if (toDeregister.isEmpty()) {
            return;
        }

        log.info("Deregistering {} webhook(s)", toDeregister.size());

        Map<String, WebhookProvider> providerMap = providers.stream()
                .collect(Collectors.toMap(WebhookProvider::name, Function.identity(), (a, b) -> a));

        for (WebhookRegistrationResult registration : toDeregister) {
            WebhookProvider provider = providerMap.get(registration.provider());
            if (provider == null) {
                log.warn("No provider found for '{}' — cannot deregister webhook {}",
                        registration.provider(), registration.webhookId());
                continue;
            }

            try {
                provider.deregister(registration);
                log.info("Webhook '{}' deregistered (ID: {})", registration.provider(), registration.webhookId());
            } catch (Exception e) {
                log.warn("Failed to deregister webhook '{}' (ID: {}): {}",
                        registration.provider(), registration.webhookId(), e.getMessage());
            }
        }
    }
}
