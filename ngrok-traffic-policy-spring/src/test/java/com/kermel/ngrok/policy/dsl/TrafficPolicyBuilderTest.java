package com.kermel.ngrok.policy.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrafficPolicyBuilder — fluent DSL")
class TrafficPolicyBuilderTest {

    @Test
    void emptySinglePhase() {
        List<PolicyRule> rules = TrafficPolicy.builder().build();
        assertThat(rules).isEmpty();
    }

    @Test
    void singleRuleOnHttpRequest() {
        List<PolicyRule> rules = TrafficPolicy.builder()
                .onHttpRequest()
                    .rule("Rate limit")
                        .action(PolicyAction.rateLimit().build())
                .build();

        assertThat(rules).hasSize(1);
        PolicyRule rule = rules.get(0);
        assertThat(rule.phase()).isEqualTo(PolicyRule.Phase.ON_HTTP_REQUEST);
        assertThat(rule.name()).isEqualTo("Rate limit");
        assertThat(rule.action().getType()).isEqualTo("rate-limit");
        assertThat(rule.expressions()).isEmpty();
    }

    @Test
    void ruleWithExpressionAndOrder() {
        List<PolicyRule> rules = TrafficPolicy.builder()
                .onHttpRequest()
                    .rule("Block bots")
                        .expression("'bot' in req.headers['user-agent']")
                        .order(5)
                        .action(PolicyAction.deny(403))
                .build();

        PolicyRule rule = rules.get(0);
        assertThat(rule.expressions()).containsExactly("'bot' in req.headers['user-agent']");
        assertThat(rule.order()).isEqualTo(5);
    }

    @Test
    void multipleRulesInSamePhase() {
        List<PolicyRule> rules = TrafficPolicy.builder()
                .onHttpRequest()
                    .rule("Rate limit")
                        .action(PolicyAction.rateLimit().build())
                    .rule("Block bots")
                        .expression("'bot' in req.headers['user-agent']")
                        .action(PolicyAction.deny(403))
                .build();

        assertThat(rules).hasSize(2);
        assertThat(rules).allMatch(r -> r.phase() == PolicyRule.Phase.ON_HTTP_REQUEST);
    }

    @Test
    void multiplePhases() {
        List<PolicyRule> rules = TrafficPolicy.builder()
                .onHttpRequest()
                    .rule("Rate limit")
                        .action(PolicyAction.rateLimit().build())
                .onHttpResponse()
                    .rule("Security headers")
                        .action(PolicyAction.addHeaders()
                                .header("X-Frame-Options", "DENY").build())
                .build();

        assertThat(rules).hasSize(2);
        assertThat(rules.get(0).phase()).isEqualTo(PolicyRule.Phase.ON_HTTP_REQUEST);
        assertThat(rules.get(1).phase()).isEqualTo(PolicyRule.Phase.ON_HTTP_RESPONSE);
    }

    @Test
    void tcpConnectPhase() {
        List<PolicyRule> rules = TrafficPolicy.builder()
                .onTcpConnect()
                    .rule("Log TCP")
                        .action(PolicyAction.log().build())
                .build();

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).phase()).isEqualTo(PolicyRule.Phase.ON_TCP_CONNECT);
    }

    @Test
    void complexMultiPhasePolicy() {
        List<PolicyRule> rules = TrafficPolicy.builder()
                .onHttpRequest()
                    .rule("Rate limit API")
                        .expression("req.url.path.startsWith('/api')")
                        .order(1)
                        .action(PolicyAction.rateLimit()
                                .capacity(100).rate("60s")
                                .bucketKey("conn.client_ip").build())
                    .rule("Block bots")
                        .expression("'bot' in req.headers['user-agent']")
                        .order(2)
                        .action(PolicyAction.deny(403))
                    .rule("JWT validation")
                        .expression("req.url.path.startsWith('/api/protected')")
                        .order(3)
                        .action(PolicyAction.jwt()
                                .issuer("https://auth.example.com")
                                .audience("my-api").build())
                .onHttpResponse()
                    .rule("CORS headers")
                        .action(PolicyAction.addHeaders()
                                .header("Access-Control-Allow-Origin", "*").build())
                    .rule("Compress")
                        .action(PolicyAction.compressResponse()
                                .algorithms("gzip", "br").build())
                .build();

        assertThat(rules).hasSize(5);

        long requestRules = rules.stream()
                .filter(r -> r.phase() == PolicyRule.Phase.ON_HTTP_REQUEST).count();
        long responseRules = rules.stream()
                .filter(r -> r.phase() == PolicyRule.Phase.ON_HTTP_RESPONSE).count();

        assertThat(requestRules).isEqualTo(3);
        assertThat(responseRules).isEqualTo(2);
    }

    @Test
    void phaseBuilderReturnsToParent() {
        List<PolicyRule> rules = TrafficPolicy.builder()
                .onHttpRequest()
                    .rule("A")
                        .action(PolicyAction.deny(403))
                .onHttpResponse()
                    .rule("B")
                        .action(PolicyAction.log().build())
                .onTcpConnect()
                    .rule("C")
                        .action(PolicyAction.deny(403))
                .build();

        assertThat(rules).hasSize(3);
        assertThat(rules.get(0).phase()).isEqualTo(PolicyRule.Phase.ON_HTTP_REQUEST);
        assertThat(rules.get(1).phase()).isEqualTo(PolicyRule.Phase.ON_HTTP_RESPONSE);
        assertThat(rules.get(2).phase()).isEqualTo(PolicyRule.Phase.ON_TCP_CONNECT);
    }
}
