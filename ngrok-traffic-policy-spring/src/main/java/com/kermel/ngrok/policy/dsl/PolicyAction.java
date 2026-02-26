package com.kermel.ngrok.policy.dsl;

import java.util.Map;

/**
 * Base class for all ngrok Traffic Policy actions.
 *
 * <p>Provides static factory methods as entry points for the fluent API:
 * <pre>{@code
 * PolicyAction.deny(403)
 * PolicyAction.rateLimit().capacity(100).rate("60s").build()
 * PolicyAction.addHeaders().header("X-Frame-Options", "DENY").build()
 * }</pre>
 */
public abstract class PolicyAction {

    /** The ngrok action type identifier (e.g., "deny", "rate-limit", "jwt"). */
    public abstract String getType();

    /** The action configuration map, serialized into the YAML config block. */
    public abstract Map<String, Object> getConfig();

    // --- Static Factory Methods ---

    public static DenyAction deny(int statusCode) {
        return new DenyAction(statusCode, null);
    }

    public static DenyAction deny(int statusCode, String message) {
        return new DenyAction(statusCode, message);
    }

    public static RateLimitAction.Builder rateLimit() {
        return new RateLimitAction.Builder();
    }

    public static JwtAction.Builder jwt() {
        return new JwtAction.Builder();
    }

    public static CustomResponseAction.Builder customResponse() {
        return new CustomResponseAction.Builder();
    }

    public static RedirectAction redirect(String url) {
        return new RedirectAction(url, 301);
    }

    public static RedirectAction redirect(String url, int statusCode) {
        return new RedirectAction(url, statusCode);
    }

    public static AddHeadersAction.Builder addHeaders() {
        return new AddHeadersAction.Builder();
    }

    public static RemoveHeadersAction.Builder removeHeaders() {
        return new RemoveHeadersAction.Builder();
    }

    public static UrlRewriteAction.Builder urlRewrite() {
        return new UrlRewriteAction.Builder();
    }

    public static CompressResponseAction.Builder compressResponse() {
        return new CompressResponseAction.Builder();
    }

    public static LogAction.Builder log() {
        return new LogAction.Builder();
    }
}
