package com.kermel.ngrok.policy.autoconfigure;

import com.kermel.ngrok.autoconfigure.NgrokProperties;
import com.kermel.ngrok.policy.annotation.NgrokTrafficPolicy;
import com.kermel.ngrok.policy.annotation.OnHttpRequest;
import com.kermel.ngrok.policy.annotation.OnHttpResponse;
import com.kermel.ngrok.policy.dsl.PolicyAction;
import com.kermel.ngrok.policy.generator.TrafficPolicyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrafficPolicyAutoConfiguration")
class TrafficPolicyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TrafficPolicyAutoConfiguration.class));

    @Test
    void generatorBeanCreatedWhenNgrokPropertiesPresent() {
        contextRunner
                .withBean(NgrokProperties.class)
                .withUserConfiguration(SamplePolicyConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TrafficPolicyGenerator.class);
                });
    }

    @Test
    void noGeneratorBeanWithoutNgrokProperties() {
        contextRunner
                .withUserConfiguration(SamplePolicyConfig.class)
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(TrafficPolicyGenerator.class);
                });
    }

    @Test
    void policyInjectedIntoDefaultTunnel() {
        contextRunner
                .withBean(NgrokProperties.class)
                .withUserConfiguration(SamplePolicyConfig.class)
                .run(ctx -> {
                    NgrokProperties properties = ctx.getBean(NgrokProperties.class);
                    assertThat(properties.getDefaultTunnel().getTrafficPolicy()).isNotNull();
                    assertThat(properties.getDefaultTunnel().getTrafficPolicy()).contains("type: deny");
                });
    }

    @Test
    void policyNotInjectedWhenAlreadyConfigured() {
        contextRunner
                .withBean(NgrokProperties.class, () -> {
                    NgrokProperties props = new NgrokProperties();
                    props.getDefaultTunnel().setTrafficPolicy("existing: policy");
                    return props;
                })
                .withUserConfiguration(SamplePolicyConfig.class)
                .run(ctx -> {
                    NgrokProperties properties = ctx.getBean(NgrokProperties.class);
                    assertThat(properties.getDefaultTunnel().getTrafficPolicy()).isEqualTo("existing: policy");
                });
    }

    @Test
    void policyNotInjectedWhenFileConfigured() {
        contextRunner
                .withBean(NgrokProperties.class, () -> {
                    NgrokProperties props = new NgrokProperties();
                    props.getDefaultTunnel().setTrafficPolicyFile("policy.yml");
                    return props;
                })
                .withUserConfiguration(SamplePolicyConfig.class)
                .run(ctx -> {
                    NgrokProperties properties = ctx.getBean(NgrokProperties.class);
                    assertThat(properties.getDefaultTunnel().getTrafficPolicy()).isNull();
                });
    }

    @Test
    void namedTunnelPolicyInjected() {
        contextRunner
                .withBean(NgrokProperties.class, () -> {
                    NgrokProperties props = new NgrokProperties();
                    props.getTunnels().put("api", new NgrokProperties.TunnelProperties());
                    return props;
                })
                .withUserConfiguration(NamedTunnelPolicyConfig.class)
                .run(ctx -> {
                    NgrokProperties properties = ctx.getBean(NgrokProperties.class);
                    NgrokProperties.TunnelProperties tunnelProps = properties.getTunnels().get("api");
                    assertThat(tunnelProps.getTrafficPolicy()).isNotNull();
                    assertThat(tunnelProps.getTrafficPolicy()).contains("type: rate-limit");
                });
    }

    @Test
    void namedTunnelPolicySkippedWhenTunnelNotConfigured() {
        contextRunner
                .withBean(NgrokProperties.class)
                .withUserConfiguration(NamedTunnelPolicyConfig.class)
                .run(ctx -> {
                    // No "api" tunnel configured — policy should be skipped, no error
                    assertThat(ctx).hasSingleBean(TrafficPolicyGenerator.class);
                    TrafficPolicyGenerator generator = ctx.getBean(TrafficPolicyGenerator.class);
                    assertThat(generator.hasPolicies()).isTrue();
                });
    }

    @Test
    void noPolicyBeansProducesNoInjection() {
        contextRunner
                .withBean(NgrokProperties.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(TrafficPolicyGenerator.class);
                    NgrokProperties properties = ctx.getBean(NgrokProperties.class);
                    assertThat(properties.getDefaultTunnel().getTrafficPolicy()).isNull();
                });
    }

    @Test
    void multiPhasePolicy() {
        contextRunner
                .withBean(NgrokProperties.class)
                .withUserConfiguration(MultiPhasePolicyConfig.class)
                .run(ctx -> {
                    NgrokProperties properties = ctx.getBean(NgrokProperties.class);
                    String yaml = properties.getDefaultTunnel().getTrafficPolicy();
                    assertThat(yaml).contains("on_http_request:");
                    assertThat(yaml).contains("on_http_response:");
                });
    }

    // --- Test configurations ---

    @Configuration
    static class SamplePolicyConfig {
        @Bean
        public SamplePolicy samplePolicy() {
            return new SamplePolicy();
        }
    }

    @NgrokTrafficPolicy
    static class SamplePolicy {
        @OnHttpRequest(name = "Block")
        public PolicyAction block() {
            return PolicyAction.deny(403);
        }
    }

    @Configuration
    static class NamedTunnelPolicyConfig {
        @Bean
        public ApiTunnelPolicy apiTunnelPolicy() {
            return new ApiTunnelPolicy();
        }
    }

    @NgrokTrafficPolicy(tunnel = "api")
    static class ApiTunnelPolicy {
        @OnHttpRequest(name = "Rate limit")
        public PolicyAction rateLimit() {
            return PolicyAction.rateLimit().build();
        }
    }

    @Configuration
    static class MultiPhasePolicyConfig {
        @Bean
        public MultiPhasePolicy multiPhasePolicy() {
            return new MultiPhasePolicy();
        }
    }

    @NgrokTrafficPolicy
    static class MultiPhasePolicy {
        @OnHttpRequest(name = "Log")
        public PolicyAction logRequest() {
            return PolicyAction.log().metadata("phase", "request").build();
        }

        @OnHttpResponse(name = "Headers")
        public PolicyAction headers() {
            return PolicyAction.addHeaders().header("X-Test", "true").build();
        }
    }
}
