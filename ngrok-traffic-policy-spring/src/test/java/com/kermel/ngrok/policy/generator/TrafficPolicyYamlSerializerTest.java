package com.kermel.ngrok.policy.generator;

import com.kermel.ngrok.policy.dsl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrafficPolicyYamlSerializer")
class TrafficPolicyYamlSerializerTest {

    private TrafficPolicyYamlSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new TrafficPolicyYamlSerializer();
    }

    @Test
    void nullRulesReturnsNull() {
        assertThat(serializer.serialize(null)).isNull();
    }

    @Test
    void emptyRulesReturnsNull() {
        assertThat(serializer.serialize(Collections.emptyList())).isNull();
    }

    @Test
    void singleDenyRule() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_REQUEST, "Block",
                        List.of(), PolicyAction.deny(403), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).isNotNull();
        assertThat(yaml).contains("on_http_request:");
        assertThat(yaml).contains("name: Block");
        assertThat(yaml).contains("type: deny");
        assertThat(yaml).contains("status_code: 403");
    }

    @Test
    void ruleWithExpressions() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_REQUEST, "Filter",
                        List.of("req.url.path.startsWith('/api')"),
                        PolicyAction.deny(403), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("expressions:");
        assertThat(yaml).contains("req.url.path.startsWith('/api')");
    }

    @Test
    void ruleWithEmptyNameOmitsName() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_REQUEST, "",
                        List.of(), PolicyAction.deny(403), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).doesNotContain("name:");
    }

    @Test
    void multiplePhases() {
        List<PolicyRule> rules = TrafficPolicy.builder()
                .onHttpRequest()
                    .rule("Rate limit")
                        .action(PolicyAction.rateLimit().capacity(50).rate("30s").build())
                .onHttpResponse()
                    .rule("Headers")
                        .action(PolicyAction.addHeaders()
                                .header("X-Frame-Options", "DENY").build())
                .build();

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("on_http_request:");
        assertThat(yaml).contains("on_http_response:");
        assertThat(yaml).contains("type: rate-limit");
        assertThat(yaml).contains("type: add-headers");
    }

    @Test
    void tcpConnectPhase() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_TCP_CONNECT, "Log TCP",
                        List.of(), PolicyAction.log().metadata("event", "connect").build(), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("on_tcp_connect:");
        assertThat(yaml).contains("type: log");
    }

    @Test
    void rulesAreSortedByOrderWithinPhase() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_REQUEST, "Second",
                        List.of(), PolicyAction.deny(403), 10),
                new PolicyRule(PolicyRule.Phase.ON_HTTP_REQUEST, "First",
                        List.of(), PolicyAction.deny(401), 1)
        );

        String yaml = serializer.serialize(rules);
        int firstIdx = yaml.indexOf("name: First");
        int secondIdx = yaml.indexOf("name: Second");
        assertThat(firstIdx).isLessThan(secondIdx);
    }

    @Test
    void rateLimitActionConfig() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_REQUEST, "RL",
                        List.of(), PolicyAction.rateLimit()
                                .algorithm("sliding_window")
                                .capacity(100)
                                .rate("60s")
                                .bucketKey("conn.client_ip")
                                .build(), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("algorithm: sliding_window");
        assertThat(yaml).contains("capacity: 100");
        assertThat(yaml).contains("rate: 60s");
        assertThat(yaml).contains("bucket_key:");
        assertThat(yaml).contains("conn.client_ip");
    }

    @Test
    void addHeadersActionConfig() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_RESPONSE, "Sec",
                        List.of(), PolicyAction.addHeaders()
                                .header("X-Frame-Options", "DENY")
                                .header("X-Content-Type-Options", "nosniff")
                                .build(), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("headers:");
        assertThat(yaml).contains("X-Frame-Options: DENY");
        assertThat(yaml).contains("X-Content-Type-Options: nosniff");
    }

    @Test
    void removeHeadersActionConfig() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_RESPONSE, "Clean",
                        List.of(), PolicyAction.removeHeaders()
                                .header("X-Powered-By")
                                .header("Server")
                                .build(), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("type: remove-headers");
        assertThat(yaml).contains("- X-Powered-By");
        assertThat(yaml).contains("- Server");
    }

    @Test
    void redirectActionConfig() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_REQUEST, "Redirect",
                        List.of(), PolicyAction.redirect("https://example.com", 302), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("type: redirect");
        assertThat(yaml).contains("to: https://example.com");
        assertThat(yaml).contains("status_code: 302");
    }

    @Test
    void urlRewriteActionConfig() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_REQUEST, "Rewrite",
                        List.of(), PolicyAction.urlRewrite()
                                .from("/old/(.*)")
                                .to("/new/$1")
                                .build(), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("type: url-rewrite");
        assertThat(yaml).contains("from: /old/(.*)");
        assertThat(yaml).contains("to: /new/$1");
    }

    @Test
    void compressResponseActionConfig() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_RESPONSE, "Compress",
                        List.of(), PolicyAction.compressResponse()
                                .algorithms("gzip", "br")
                                .build(), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("type: compress-response");
        assertThat(yaml).contains("- gzip");
        assertThat(yaml).contains("- br");
    }

    @Test
    void customResponseActionConfig() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_REQUEST, "Maintenance",
                        List.of(), PolicyAction.customResponse()
                                .statusCode(503)
                                .content("Under maintenance")
                                .header("Retry-After", "3600")
                                .build(), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("type: custom-response");
        assertThat(yaml).contains("status_code: 503");
        assertThat(yaml).contains("content: Under maintenance");
        assertThat(yaml).contains("Retry-After: '3600'");
    }

    @Test
    void actionWithNoConfigOmitsConfigBlock() {
        List<PolicyRule> rules = List.of(
                new PolicyRule(PolicyRule.Phase.ON_HTTP_RESPONSE, "Compress",
                        List.of(), PolicyAction.compressResponse().build(), 0)
        );

        String yaml = serializer.serialize(rules);
        assertThat(yaml).contains("type: compress-response");
        // No config block since no algorithms specified
        assertThat(yaml).doesNotContain("algorithms:");
    }

    @Test
    void validYamlStructure() {
        List<PolicyRule> rules = TrafficPolicy.builder()
                .onHttpRequest()
                    .rule("A")
                        .expression("true")
                        .action(PolicyAction.deny(403, "Blocked"))
                .build();

        String yaml = serializer.serialize(rules);

        // Verify block-style YAML (no curly braces)
        assertThat(yaml).doesNotContain("{");
        assertThat(yaml).doesNotContain("}");
    }
}
