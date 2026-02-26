package com.kermel.ngrok.policy.dsl;

import java.util.Arrays;
import java.util.List;

/**
 * Represents a single traffic policy rule with its phase, name, CEL expressions,
 * action, and execution order.
 */
public record PolicyRule(
        Phase phase,
        String name,
        List<String> expressions,
        PolicyAction action,
        int order
) {

    /** The traffic policy phase this rule belongs to. */
    public enum Phase {
        ON_HTTP_REQUEST("on_http_request"),
        ON_HTTP_RESPONSE("on_http_response"),
        ON_TCP_CONNECT("on_tcp_connect");

        private final String yamlKey;

        Phase(String yamlKey) {
            this.yamlKey = yamlKey;
        }

        public String yamlKey() {
            return yamlKey;
        }
    }

    public PolicyRule(Phase phase, String name, PolicyAction action, int order, String... expressions) {
        this(phase, name, Arrays.asList(expressions), action, order);
    }
}
