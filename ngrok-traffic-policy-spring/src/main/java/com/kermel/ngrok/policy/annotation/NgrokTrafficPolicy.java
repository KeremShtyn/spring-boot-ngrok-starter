package com.kermel.ngrok.policy.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a ngrok Traffic Policy definition.
 *
 * <p>Methods annotated with {@link OnHttpRequest}, {@link OnHttpResponse},
 * or {@link OnTcpConnect} define policy rules that are compiled into
 * ngrok Traffic Policy YAML at startup.
 *
 * <pre>{@code
 * @NgrokTrafficPolicy
 * public class MyGatewayPolicy {
 *
 *     @OnHttpRequest(name = "Rate limit API", expressions = "req.url.path.startsWith('/api')")
 *     public PolicyAction rateLimitApi() {
 *         return PolicyAction.rateLimit()
 *             .capacity(100).rate("60s").bucketKey("conn.client_ip").build();
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface NgrokTrafficPolicy {

    /** Optional tunnel name this policy applies to. Empty = default tunnel. */
    String tunnel() default "";
}
