package com.kermel.ngrok.policy.dsl;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for constructing ngrok Traffic Policies programmatically.
 *
 * <pre>{@code
 * TrafficPolicy policy = TrafficPolicy.builder()
 *     .onHttpRequest()
 *         .rule("Rate limit")
 *             .action(PolicyAction.rateLimit().capacity(100).rate("60s").build())
 *         .rule("Block bots")
 *             .expression("'bot' in req.headers['user-agent']")
 *             .action(PolicyAction.deny(403))
 *     .onHttpResponse()
 *         .rule("CORS headers")
 *             .action(PolicyAction.addHeaders().header("Access-Control-Allow-Origin", "*").build())
 *     .build();
 * }</pre>
 */
public class TrafficPolicyBuilder {

    private final List<PolicyRule> rules = new ArrayList<>();

    /**
     * Start adding rules for the {@code on_http_request} phase.
     */
    public PhaseBuilder onHttpRequest() {
        return new PhaseBuilder(this, PolicyRule.Phase.ON_HTTP_REQUEST);
    }

    /**
     * Start adding rules for the {@code on_http_response} phase.
     */
    public PhaseBuilder onHttpResponse() {
        return new PhaseBuilder(this, PolicyRule.Phase.ON_HTTP_RESPONSE);
    }

    /**
     * Start adding rules for the {@code on_tcp_connect} phase.
     */
    public PhaseBuilder onTcpConnect() {
        return new PhaseBuilder(this, PolicyRule.Phase.ON_TCP_CONNECT);
    }

    void addRule(PolicyRule rule) {
        rules.add(rule);
    }

    /**
     * Build the final list of policy rules.
     */
    public List<PolicyRule> build() {
        return List.copyOf(rules);
    }

    /**
     * Builder for rules within a specific phase.
     */
    public static class PhaseBuilder {
        private final TrafficPolicyBuilder parent;
        private final PolicyRule.Phase phase;

        PhaseBuilder(TrafficPolicyBuilder parent, PolicyRule.Phase phase) {
            this.parent = parent;
            this.phase = phase;
        }

        /**
         * Start building a new rule with the given name.
         */
        public RuleBuilder rule(String name) {
            return new RuleBuilder(this, phase, name);
        }

        /** Return to the parent builder to start a new phase. */
        public PhaseBuilder onHttpRequest() {
            return parent.onHttpRequest();
        }

        /** Return to the parent builder to start a new phase. */
        public PhaseBuilder onHttpResponse() {
            return parent.onHttpResponse();
        }

        /** Return to the parent builder to start a new phase. */
        public PhaseBuilder onTcpConnect() {
            return parent.onTcpConnect();
        }

        /** Build the final policy. */
        public List<PolicyRule> build() {
            return parent.build();
        }

        void addRule(PolicyRule rule) {
            parent.addRule(rule);
        }
    }

    /**
     * Builder for an individual rule.
     */
    public static class RuleBuilder {
        private final PhaseBuilder phaseBuilder;
        private final PolicyRule.Phase phase;
        private final String name;
        private final List<String> expressions = new ArrayList<>();
        private int order = 0;

        RuleBuilder(PhaseBuilder phaseBuilder, PolicyRule.Phase phase, String name) {
            this.phaseBuilder = phaseBuilder;
            this.phase = phase;
            this.name = name;
        }

        public RuleBuilder expression(String expression) {
            this.expressions.add(expression);
            return this;
        }

        public RuleBuilder order(int order) {
            this.order = order;
            return this;
        }

        /**
         * Set the action for this rule and finalize it.
         * Returns the phase builder for chaining.
         */
        public PhaseBuilder action(PolicyAction action) {
            phaseBuilder.addRule(new PolicyRule(phase, name, expressions, action, order));
            return phaseBuilder;
        }
    }
}
