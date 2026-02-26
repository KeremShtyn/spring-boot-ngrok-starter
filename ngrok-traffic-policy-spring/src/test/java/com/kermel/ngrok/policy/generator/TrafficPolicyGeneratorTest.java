package com.kermel.ngrok.policy.generator;

import com.kermel.ngrok.policy.annotation.NgrokTrafficPolicy;
import com.kermel.ngrok.policy.annotation.OnHttpRequest;
import com.kermel.ngrok.policy.annotation.OnHttpResponse;
import com.kermel.ngrok.policy.annotation.OnTcpConnect;
import com.kermel.ngrok.policy.dsl.PolicyAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrafficPolicyGenerator — annotation scanning")
class TrafficPolicyGeneratorTest {

    @Test
    void noPolicyBeans() {
        try (var ctx = new AnnotationConfigApplicationContext(EmptyConfig.class)) {
            TrafficPolicyGenerator generator = new TrafficPolicyGenerator(ctx);
            generator.generate();

            assertThat(generator.hasPolicies()).isFalse();
            assertThat(generator.getDefaultPolicy()).isNull();
            assertThat(generator.getAllPolicies()).isEmpty();
        }
    }

    @Test
    void singleDefaultPolicy() {
        try (var ctx = new AnnotationConfigApplicationContext(DefaultPolicyConfig.class)) {
            TrafficPolicyGenerator generator = new TrafficPolicyGenerator(ctx);
            generator.generate();

            assertThat(generator.hasPolicies()).isTrue();
            String yaml = generator.getDefaultPolicy();
            assertThat(yaml).isNotNull();
            assertThat(yaml).contains("on_http_request:");
            assertThat(yaml).contains("type: deny");
            assertThat(yaml).contains("status_code: 403");
        }
    }

    @Test
    void namedTunnelPolicy() {
        try (var ctx = new AnnotationConfigApplicationContext(NamedTunnelPolicyConfig.class)) {
            TrafficPolicyGenerator generator = new TrafficPolicyGenerator(ctx);
            generator.generate();

            assertThat(generator.hasPolicies()).isTrue();
            assertThat(generator.getDefaultPolicy()).isNull();
            assertThat(generator.getGeneratedPolicy("api-tunnel")).isNotNull();
            assertThat(generator.getGeneratedPolicy("api-tunnel")).contains("type: rate-limit");
        }
    }

    @Test
    void multipleAnnotationsOnSameBean() {
        try (var ctx = new AnnotationConfigApplicationContext(MultiAnnotationConfig.class)) {
            TrafficPolicyGenerator generator = new TrafficPolicyGenerator(ctx);
            generator.generate();

            String yaml = generator.getDefaultPolicy();
            assertThat(yaml).isNotNull();
            assertThat(yaml).contains("on_http_request:");
            assertThat(yaml).contains("on_http_response:");
        }
    }

    @Test
    void tcpConnectAnnotation() {
        try (var ctx = new AnnotationConfigApplicationContext(TcpPolicyConfig.class)) {
            TrafficPolicyGenerator generator = new TrafficPolicyGenerator(ctx);
            generator.generate();

            String yaml = generator.getDefaultPolicy();
            assertThat(yaml).isNotNull();
            assertThat(yaml).contains("on_tcp_connect:");
            assertThat(yaml).contains("type: log");
        }
    }

    @Test
    void expressionsFromAnnotation() {
        try (var ctx = new AnnotationConfigApplicationContext(ExpressionPolicyConfig.class)) {
            TrafficPolicyGenerator generator = new TrafficPolicyGenerator(ctx);
            generator.generate();

            String yaml = generator.getDefaultPolicy();
            assertThat(yaml).contains("expressions:");
            assertThat(yaml).contains("req.url.path.startsWith('/api')");
        }
    }

    @Test
    void methodReturningNonPolicyActionIsSkipped() {
        try (var ctx = new AnnotationConfigApplicationContext(BadReturnTypeConfig.class)) {
            TrafficPolicyGenerator generator = new TrafficPolicyGenerator(ctx);
            generator.generate();

            // The bad method returns String, not PolicyAction — should be skipped
            assertThat(generator.hasPolicies()).isFalse();
        }
    }

    @Test
    void ruleNameFromAnnotation() {
        try (var ctx = new AnnotationConfigApplicationContext(NamedRuleConfig.class)) {
            TrafficPolicyGenerator generator = new TrafficPolicyGenerator(ctx);
            generator.generate();

            String yaml = generator.getDefaultPolicy();
            assertThat(yaml).contains("name: Block bots");
        }
    }

    @Test
    void multiplePolicyBeans() {
        try (var ctx = new AnnotationConfigApplicationContext(MultiBeanConfig.class)) {
            TrafficPolicyGenerator generator = new TrafficPolicyGenerator(ctx);
            generator.generate();

            String defaultYaml = generator.getDefaultPolicy();
            String apiYaml = generator.getGeneratedPolicy("api");

            assertThat(defaultYaml).isNotNull();
            assertThat(apiYaml).isNotNull();
            assertThat(defaultYaml).contains("type: deny");
            assertThat(apiYaml).contains("type: rate-limit");
        }
    }

    // --- Test configurations ---

    @Configuration
    static class EmptyConfig {}

    @Configuration
    static class DefaultPolicyConfig {
        @Bean
        public DefaultPolicy defaultPolicy() {
            return new DefaultPolicy();
        }
    }

    @NgrokTrafficPolicy
    static class DefaultPolicy {
        @OnHttpRequest(name = "Block all")
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

    @NgrokTrafficPolicy(tunnel = "api-tunnel")
    static class ApiTunnelPolicy {
        @OnHttpRequest(name = "Rate limit")
        public PolicyAction rateLimit() {
            return PolicyAction.rateLimit().capacity(50).rate("30s").build();
        }
    }

    @Configuration
    static class MultiAnnotationConfig {
        @Bean
        public MultiPhasePolicy multiPhasePolicy() {
            return new MultiPhasePolicy();
        }
    }

    @NgrokTrafficPolicy
    static class MultiPhasePolicy {
        @OnHttpRequest(name = "Block")
        public PolicyAction block() {
            return PolicyAction.deny(403);
        }

        @OnHttpResponse(name = "Headers")
        public PolicyAction headers() {
            return PolicyAction.addHeaders().header("X-Test", "true").build();
        }
    }

    @Configuration
    static class TcpPolicyConfig {
        @Bean
        public TcpPolicy tcpPolicy() {
            return new TcpPolicy();
        }
    }

    @NgrokTrafficPolicy
    static class TcpPolicy {
        @OnTcpConnect(name = "Log TCP")
        public PolicyAction logTcp() {
            return PolicyAction.log().metadata("event", "tcp_connect").build();
        }
    }

    @Configuration
    static class ExpressionPolicyConfig {
        @Bean
        public ExpressionPolicy expressionPolicy() {
            return new ExpressionPolicy();
        }
    }

    @NgrokTrafficPolicy
    static class ExpressionPolicy {
        @OnHttpRequest(name = "API only", expressions = "req.url.path.startsWith('/api')")
        public PolicyAction apiOnly() {
            return PolicyAction.rateLimit().build();
        }
    }

    @Configuration
    static class BadReturnTypeConfig {
        @Bean
        public BadReturnPolicy badReturnPolicy() {
            return new BadReturnPolicy();
        }
    }

    @NgrokTrafficPolicy
    static class BadReturnPolicy {
        @OnHttpRequest(name = "Bad")
        public String notAPolicyAction() {
            return "this is wrong";
        }
    }

    @Configuration
    static class NamedRuleConfig {
        @Bean
        public NamedRulePolicy namedRulePolicy() {
            return new NamedRulePolicy();
        }
    }

    @NgrokTrafficPolicy
    static class NamedRulePolicy {
        @OnHttpRequest(name = "Block bots", expressions = "'bot' in req.headers['user-agent']")
        public PolicyAction blockBots() {
            return PolicyAction.deny(403, "Bot traffic not allowed");
        }
    }

    @Configuration
    static class MultiBeanConfig {
        @Bean
        public DefaultPolicy2 defaultPolicy() {
            return new DefaultPolicy2();
        }

        @Bean
        public ApiPolicy apiPolicy() {
            return new ApiPolicy();
        }
    }

    @NgrokTrafficPolicy
    static class DefaultPolicy2 {
        @OnHttpRequest
        public PolicyAction block() {
            return PolicyAction.deny(403);
        }
    }

    @NgrokTrafficPolicy(tunnel = "api")
    static class ApiPolicy {
        @OnHttpRequest
        public PolicyAction rateLimit() {
            return PolicyAction.rateLimit().build();
        }
    }
}
