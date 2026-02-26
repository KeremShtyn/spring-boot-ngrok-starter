package com.kermel.ngrok.policy.autoconfigure;

import com.kermel.ngrok.autoconfigure.NgrokProperties;
import com.kermel.ngrok.policy.generator.TrafficPolicyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * Auto-configuration for the ngrok Traffic Policy DSL module.
 *
 * <p>Scans for {@code @NgrokTrafficPolicy} beans, generates YAML policies,
 * and injects them into the tunnel properties before tunnels are created.
 */
@AutoConfiguration
@ConditionalOnBean(NgrokProperties.class)
public class TrafficPolicyAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TrafficPolicyAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public TrafficPolicyGenerator trafficPolicyGenerator(ApplicationContext applicationContext,
                                                          NgrokProperties properties) {
        TrafficPolicyGenerator generator = new TrafficPolicyGenerator(applicationContext);
        generator.generate();

        // Inject generated policies into tunnel properties
        if (generator.hasPolicies()) {
            injectPolicies(generator, properties);
        }

        return generator;
    }

    private void injectPolicies(TrafficPolicyGenerator generator, NgrokProperties properties) {
        Map<String, String> policies = generator.getAllPolicies();

        for (Map.Entry<String, String> entry : policies.entrySet()) {
            String tunnelName = entry.getKey();
            String yaml = entry.getValue();

            if (tunnelName.isEmpty()) {
                // Default tunnel
                NgrokProperties.TunnelProperties defaultTunnel = properties.getDefaultTunnel();
                if (defaultTunnel.getTrafficPolicy() == null && defaultTunnel.getTrafficPolicyFile() == null) {
                    defaultTunnel.setTrafficPolicy(yaml);
                    log.debug("Injected generated traffic policy into default tunnel");
                } else {
                    log.debug("Default tunnel already has a traffic policy configured — skipping DSL-generated policy");
                }
            } else {
                // Named tunnel
                NgrokProperties.TunnelProperties tunnelProps = properties.getTunnels().get(tunnelName);
                if (tunnelProps != null) {
                    if (tunnelProps.getTrafficPolicy() == null && tunnelProps.getTrafficPolicyFile() == null) {
                        tunnelProps.setTrafficPolicy(yaml);
                        log.debug("Injected generated traffic policy into tunnel '{}'", tunnelName);
                    } else {
                        log.debug("Tunnel '{}' already has a traffic policy configured — skipping DSL-generated policy",
                                tunnelName);
                    }
                } else {
                    log.warn("@NgrokTrafficPolicy targets tunnel '{}' but no such tunnel is configured", tunnelName);
                }
            }
        }
    }
}
