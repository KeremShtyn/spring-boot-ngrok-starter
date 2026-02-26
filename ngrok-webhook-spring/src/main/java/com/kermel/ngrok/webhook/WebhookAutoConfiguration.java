package com.kermel.ngrok.webhook;

import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.webhook.lifecycle.WebhookDeregistrar;
import com.kermel.ngrok.webhook.provider.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-configuration for the webhook auto-registration module.
 *
 * <p>Creates webhook provider beans and the auto-registrar/deregistrar based
 * on the {@code ngrok.webhooks.*} configuration properties.
 */
@AutoConfiguration
@ConditionalOnBean(NgrokTunnelRegistry.class)
@EnableConfigurationProperties(WebhookProperties.class)
public class WebhookAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "ngrok.webhooks.stripe", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(StripeWebhookProvider.class)
    public StripeWebhookProvider stripeWebhookProvider(WebhookProperties properties) {
        return new StripeWebhookProvider(properties.getStripe());
    }

    @Bean
    @ConditionalOnProperty(prefix = "ngrok.webhooks.github", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(GitHubWebhookProvider.class)
    public GitHubWebhookProvider gitHubWebhookProvider(WebhookProperties properties) {
        return new GitHubWebhookProvider(properties.getGithub());
    }

    @Bean
    @ConditionalOnProperty(prefix = "ngrok.webhooks.slack", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(SlackWebhookProvider.class)
    public SlackWebhookProvider slackWebhookProvider(WebhookProperties properties) {
        return new SlackWebhookProvider(properties.getSlack());
    }

    @Bean
    @ConditionalOnProperty(prefix = "ngrok.webhooks.twilio", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(TwilioWebhookProvider.class)
    public TwilioWebhookProvider twilioWebhookProvider(WebhookProperties properties) {
        return new TwilioWebhookProvider(properties.getTwilio());
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookAutoRegistrar webhookAutoRegistrar(List<WebhookProvider> providers,
                                                      WebhookProperties properties,
                                                      NgrokTunnelRegistry tunnelRegistry) {
        return new WebhookAutoRegistrar(mergeCustomProviders(providers, properties), tunnelRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebhookDeregistrar webhookDeregistrar(WebhookAutoRegistrar registrar,
                                                  List<WebhookProvider> providers,
                                                  WebhookProperties properties) {
        return new WebhookDeregistrar(registrar, mergeCustomProviders(providers, properties));
    }

    private List<WebhookProvider> mergeCustomProviders(List<WebhookProvider> providers,
                                                        WebhookProperties properties) {
        List<WebhookProvider> allProviders = new ArrayList<>(providers);
        for (WebhookProperties.CustomWebhookConfig config : properties.getCustom()) {
            if (config.getRegistrationUrl() != null && !config.getRegistrationUrl().isBlank()) {
                allProviders.add(new CustomWebhookProvider(config));
            }
        }
        return allProviders;
    }
}
