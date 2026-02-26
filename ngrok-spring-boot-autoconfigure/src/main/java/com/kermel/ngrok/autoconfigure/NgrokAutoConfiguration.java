package com.kermel.ngrok.autoconfigure;

import com.github.alexdlaird.ngrok.NgrokClient;
import com.github.alexdlaird.ngrok.conf.JavaNgrokConfig;
import com.github.alexdlaird.ngrok.protocol.Region;
import com.kermel.ngrok.core.NgrokHealthIndicator;
import com.kermel.ngrok.core.NgrokPublicUrlProvider;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/**
 * Auto-configuration for ngrok integration with Spring Boot.
 *
 * <p>Activates when:
 * <ul>
 *   <li>{@code NgrokClient} is on the classpath</li>
 *   <li>{@code ngrok.enabled} is {@code true} (default)</li>
 *   <li>Active Spring profiles match (if profile-restricted)</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(NgrokClient.class)
@ConditionalOnProperty(prefix = "ngrok", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(NgrokCondition.class)
@EnableConfigurationProperties(NgrokProperties.class)
public class NgrokAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(NgrokAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public NgrokClient ngrokClient(NgrokProperties properties) {
        JavaNgrokConfig.Builder configBuilder = new JavaNgrokConfig.Builder();

        String authToken = resolveAuthToken(properties);
        if (authToken != null) {
            configBuilder.withAuthToken(authToken);
        }

        if (properties.getRegion() != null) {
            configBuilder.withRegion(Region.valueOf(properties.getRegion().toUpperCase()));
        }

        if (properties.getBinaryPath() != null) {
            configBuilder.withNgrokPath(java.nio.file.Paths.get(properties.getBinaryPath()));
        }

        return new NgrokClient.Builder()
                .withJavaNgrokConfig(configBuilder.build())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public NgrokTunnelRegistry ngrokTunnelRegistry() {
        return new NgrokTunnelRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public NgrokTunnelManager ngrokTunnelManager(NgrokClient ngrokClient, NgrokProperties properties) {
        return new NgrokTunnelManager(ngrokClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public NgrokBannerPrinter ngrokBannerPrinter(NgrokProperties properties) {
        return new NgrokBannerPrinter(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public NgrokPublicUrlProvider ngrokPublicUrlProvider(NgrokTunnelRegistry tunnelRegistry) {
        return new NgrokPublicUrlProvider(tunnelRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    public NgrokTunnelReconnector ngrokTunnelReconnector(NgrokClient ngrokClient,
                                                         NgrokTunnelManager tunnelManager,
                                                         NgrokTunnelRegistry tunnelRegistry,
                                                         NgrokProperties properties,
                                                         ApplicationEventPublisher eventPublisher) {
        return new NgrokTunnelReconnector(ngrokClient, tunnelManager, tunnelRegistry, properties, eventPublisher);
    }

    @Bean
    @ConditionalOnMissingBean
    public NgrokLifecycle ngrokLifecycle(NgrokTunnelManager tunnelManager,
                                         NgrokTunnelRegistry tunnelRegistry,
                                         NgrokProperties properties,
                                         NgrokBannerPrinter bannerPrinter,
                                         ApplicationEventPublisher eventPublisher,
                                         NgrokTunnelReconnector reconnector) {
        return new NgrokLifecycle(tunnelManager, tunnelRegistry, properties, bannerPrinter, eventPublisher, reconnector);
    }

    private String resolveAuthToken(NgrokProperties properties) {
        // 1. Check property
        if (properties.getAuthToken() != null && !properties.getAuthToken().isBlank()) {
            return properties.getAuthToken();
        }

        // 2. Check environment variable
        String envToken = System.getenv("NGROK_AUTHTOKEN");
        if (envToken != null && !envToken.isBlank()) {
            log.debug("Using NGROK_AUTHTOKEN from environment variable");
            return envToken;
        }

        // 3. Return null — ngrok may have a token configured in its own config file
        log.debug("No ngrok auth token configured via property or environment variable. " +
                "ngrok will use its default config if available.");
        return null;
    }
}
