package com.kermel.ngrok.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kermel.ngrok.autoconfigure.NgrokProperties;
import com.kermel.ngrok.core.NgrokTunnelRegistry;
import com.kermel.ngrok.inspector.actuator.NgrokRequestsEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Auto-configuration for the ngrok request inspection module.
 *
 * <p>Activates when:
 * <ul>
 *   <li>{@link NgrokTunnelRegistry} bean exists (ngrok is configured)</li>
 *   <li>{@code ngrok.inspection.enabled} is {@code true} (the default)</li>
 * </ul>
 *
 * <p>Creates:
 * <ul>
 *   <li>{@link InspectorClient} — HTTP client for the ngrok local API</li>
 *   <li>{@link NgrokInspector} — high-level inspection API</li>
 *   <li>{@link NgrokRequestsEndpoint} — actuator endpoint (when Actuator is present)</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnBean(NgrokTunnelRegistry.class)
@ConditionalOnProperty(name = "ngrok.inspection.enabled", matchIfMissing = true)
public class InspectorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public InspectorClient inspectorClient(NgrokProperties properties) {
        int port = properties.getInspection().getPort();
        RestClient restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .build();
        ObjectMapper objectMapper = new ObjectMapper();
        return new InspectorClient(restClient, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public NgrokInspector ngrokInspector(InspectorClient inspectorClient) {
        return new NgrokInspector(inspectorClient);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.springframework.boot.actuate.endpoint.annotation.Endpoint")
    public NgrokRequestsEndpoint ngrokRequestsEndpoint(NgrokInspector inspector) {
        return new NgrokRequestsEndpoint(inspector);
    }
}
