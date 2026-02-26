package com.kermel.ngrok.sample.policy;

import com.kermel.ngrok.policy.annotation.NgrokTrafficPolicy;
import com.kermel.ngrok.policy.annotation.OnHttpRequest;
import com.kermel.ngrok.policy.annotation.OnHttpResponse;
import com.kermel.ngrok.policy.dsl.PolicyAction;

/**
 * Example Traffic Policy using annotations.
 *
 * <p>Demonstrates rate limiting, bot blocking, security headers, and logging.
 * At startup, the annotated methods are invoked and their results are
 * serialized into ngrok Traffic Policy YAML and injected into the tunnel config.
 */
@NgrokTrafficPolicy
public class ApiGatewayPolicy {

    @OnHttpRequest(
            name = "Rate limit API",
            expressions = "req.url.path.startsWith('/api')",
            order = 1
    )
    public PolicyAction rateLimitApi() {
        return PolicyAction.rateLimit()
                .capacity(100)
                .rate("60s")
                .bucketKey("conn.client_ip")
                .build();
    }

    @OnHttpRequest(
            name = "Block bots",
            expressions = "'bot' in req.headers['user-agent']",
            order = 2
    )
    public PolicyAction blockBots() {
        return PolicyAction.deny(403, "Bot traffic is not allowed");
    }

    @OnHttpRequest(
            name = "Log all requests",
            order = 10
    )
    public PolicyAction logRequests() {
        return PolicyAction.log()
                .metadata("environment", "development")
                .metadata("app", "traffic-policy-sample")
                .build();
    }

    @OnHttpResponse(
            name = "Security headers",
            order = 1
    )
    public PolicyAction securityHeaders() {
        return PolicyAction.addHeaders()
                .header("X-Frame-Options", "DENY")
                .header("X-Content-Type-Options", "nosniff")
                .header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
                .build();
    }

    @OnHttpResponse(
            name = "Remove internal headers",
            order = 2
    )
    public PolicyAction removeInternalHeaders() {
        return PolicyAction.removeHeaders()
                .header("X-Powered-By")
                .header("Server")
                .build();
    }

    @OnHttpResponse(
            name = "Compress responses",
            order = 3
    )
    public PolicyAction compressResponses() {
        return PolicyAction.compressResponse()
                .algorithms("gzip", "br")
                .build();
    }
}
