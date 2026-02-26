package com.kermel.ngrok.policy.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyRule")
class PolicyRuleTest {

    @Test
    void recordAccessors() {
        PolicyRule rule = new PolicyRule(
                PolicyRule.Phase.ON_HTTP_REQUEST,
                "Test rule",
                List.of("expr1", "expr2"),
                PolicyAction.deny(403),
                5
        );

        assertThat(rule.phase()).isEqualTo(PolicyRule.Phase.ON_HTTP_REQUEST);
        assertThat(rule.name()).isEqualTo("Test rule");
        assertThat(rule.expressions()).containsExactly("expr1", "expr2");
        assertThat(rule.action().getType()).isEqualTo("deny");
        assertThat(rule.order()).isEqualTo(5);
    }

    @Test
    void varargConstructor() {
        PolicyRule rule = new PolicyRule(
                PolicyRule.Phase.ON_HTTP_RESPONSE,
                "Vararg rule",
                PolicyAction.deny(401),
                3,
                "expr1", "expr2"
        );

        assertThat(rule.phase()).isEqualTo(PolicyRule.Phase.ON_HTTP_RESPONSE);
        assertThat(rule.expressions()).containsExactly("expr1", "expr2");
        assertThat(rule.order()).isEqualTo(3);
    }

    @Test
    void phaseYamlKeys() {
        assertThat(PolicyRule.Phase.ON_HTTP_REQUEST.yamlKey()).isEqualTo("on_http_request");
        assertThat(PolicyRule.Phase.ON_HTTP_RESPONSE.yamlKey()).isEqualTo("on_http_response");
        assertThat(PolicyRule.Phase.ON_TCP_CONNECT.yamlKey()).isEqualTo("on_tcp_connect");
    }

    @Test
    void emptyExpressions() {
        PolicyRule rule = new PolicyRule(
                PolicyRule.Phase.ON_HTTP_REQUEST,
                "No expr",
                List.of(),
                PolicyAction.deny(403),
                0
        );

        assertThat(rule.expressions()).isEmpty();
    }
}
