package com.kermel.ngrok.actuator;

import com.kermel.ngrok.autoconfigure.NgrokProperties;
import com.kermel.ngrok.autoconfigure.NgrokTunnelReconnector;
import com.kermel.ngrok.core.NgrokHealthIndicator;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for ngrok actuator endpoints.
 * Only activates when Spring Boot Actuator is on the classpath.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
@ConditionalOnBean(NgrokTunnelRegistry.class)
public class NgrokEndpointAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnAvailableEndpoint
    public NgrokEndpoint ngrokEndpoint(NgrokTunnelRegistry tunnelRegistry,
                                       NgrokTunnelReconnector reconnector,
                                       NgrokProperties properties) {
        return new NgrokEndpoint(tunnelRegistry, reconnector, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnEnabledHealthIndicator("ngrok")
    public NgrokHealthIndicator ngrokHealthIndicator(NgrokTunnelRegistry tunnelRegistry) {
        return new NgrokHealthIndicator(tunnelRegistry);
    }
}
