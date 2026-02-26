package com.kermel.ngrok.policy.dsl;

/**
 * Entry point for the fluent Traffic Policy DSL.
 *
 * <pre>{@code
 * List<PolicyRule> rules = TrafficPolicy.builder()
 *     .onHttpRequest()
 *         .rule("Rate limit")
 *             .action(PolicyAction.rateLimit().capacity(100).rate("60s").build())
 *     .build();
 * }</pre>
 */
public final class TrafficPolicy {

    private TrafficPolicy() {}

    /**
     * Create a new Traffic Policy builder.
     */
    public static TrafficPolicyBuilder builder() {
        return new TrafficPolicyBuilder();
    }
}
