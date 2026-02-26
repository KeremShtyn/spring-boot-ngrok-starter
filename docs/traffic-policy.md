# Traffic Policy DSL

Define ngrok [Traffic Policy](https://ngrok.com/docs/traffic-policy/) rules using a Java annotation-driven API. Rules are evaluated on ngrok's cloud edge *before* traffic reaches your application.

## Setup

```xml
<dependency>
    <groupId>com.kermel</groupId>
    <artifactId>ngrok-traffic-policy-spring</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Defining Policies

Create a Spring component annotated with `@NgrokTrafficPolicy`. Define rules using `@OnHttpRequest`, `@OnHttpResponse`, or `@OnTcpConnect`:

```java
@NgrokTrafficPolicy
public class ApiGatewayPolicy {

    @OnHttpRequest(name = "rate-limit", expressions = "req.url.path.startsWith('/api')")
    public PolicyAction rateLimit() {
        return PolicyAction.rateLimit()
                .capacity(100)
                .rate("60s")
                .bucketKey("conn.client_ip")
                .build();
    }

    @OnHttpRequest(name = "block-bots",
            expressions = "req.headers['user-agent'].exists(v, v.matches('.*bot.*'))")
    public PolicyAction blockBots() {
        return PolicyAction.deny(403, "Forbidden");
    }

    @OnHttpResponse(name = "security-headers")
    public PolicyAction securityHeaders() {
        return PolicyAction.addHeaders()
                .header("X-Frame-Options", "DENY")
                .header("X-Content-Type-Options", "nosniff")
                .build();
    }
}
```

## Available Actions

### deny

Block requests with a custom status code and message:

```java
PolicyAction.deny(403, "Access denied")
```

### rate-limit

Rate limit requests:

```java
PolicyAction.rateLimit()
    .algorithm("sliding_window")  // default
    .capacity(100)
    .rate("60s")
    .bucketKey("conn.client_ip")
    .build();
```

### jwt-validation

Validate JWT tokens:

```java
PolicyAction.jwt()
    .issuer("https://auth.example.com")
    .jwksUrl("https://auth.example.com/.well-known/jwks.json")
    .audience("my-api")
    .tokenLocation("header")
    .build();
```

### custom-response

Return a custom response without forwarding to your app:

```java
PolicyAction.customResponse()
    .statusCode(200)
    .content("{\"status\": \"ok\"}")
    .header("Content-Type", "application/json")
    .build();
```

### redirect

Redirect requests:

```java
PolicyAction.redirect("https://example.com/new-path")       // 301 by default
PolicyAction.redirect("https://example.com/new-path", 302)  // custom status code
```

### add-headers

Add headers to requests or responses:

```java
PolicyAction.addHeaders()
    .header("X-Custom-Header", "value")
    .header("X-Request-ID", "${.ngrok.request_id}")
    .build();
```

### remove-headers

Remove headers:

```java
PolicyAction.removeHeaders()
    .header("Server")
    .header("X-Powered-By")
    .build();
```

### url-rewrite

Rewrite URLs:

```java
PolicyAction.urlRewrite()
    .from("/api/v1/(.*)")
    .to("/api/v2/$1")
    .build();
```

### compress-response

Compress responses:

```java
PolicyAction.compressResponse()
    .algorithms("gzip", "br")
    .build();
```

### log

Log metadata for observability:

```java
PolicyAction.log()
    .metadata("environment", "dev")
    .metadata("service", "api-gateway")
    .build();
```

## Annotations

### @NgrokTrafficPolicy

Marks a class as a Traffic Policy definition. Can specify which tunnel the policy applies to:

```java
@NgrokTrafficPolicy(tunnel = "api")  // applies to "api" tunnel only
public class ApiPolicy { ... }

@NgrokTrafficPolicy  // applies to default tunnel
public class DefaultPolicy { ... }
```

### @OnHttpRequest

Defines a rule evaluated on incoming HTTP requests:

```java
@OnHttpRequest(
    name = "rule-name",           // required: rule name
    expressions = "CEL expr",     // optional: CEL expression filter
    order = 10                    // optional: rule ordering (default 0)
)
```

### @OnHttpResponse

Defines a rule evaluated on outgoing HTTP responses. Same attributes as `@OnHttpRequest`.

### @OnTcpConnect

Defines a rule evaluated on TCP connections:

```java
@OnTcpConnect(name = "block-range", expressions = "conn.client_ip in ['10.0.0.0/8']")
public PolicyAction blockInternalRange() {
    return PolicyAction.deny(403, "Blocked");
}
```

## CEL Expressions

Traffic Policy uses [CEL (Common Expression Language)](https://ngrok.com/docs/traffic-policy/expressions/) for filtering. Common variables:

| Variable | Description |
|----------|-------------|
| `req.url.path` | Request path |
| `req.method` | HTTP method |
| `req.headers` | Request headers map |
| `conn.client_ip` | Client IP address |
| `conn.geo.country_code` | Country code |
| `res.status_code` | Response status code |

## Programmatic Builder

For dynamic policy construction, use the fluent builder:

```java
String yaml = TrafficPolicy.builder()
    .onHttpRequest()
        .rule("rate-limit")
            .expression("req.url.path.startsWith('/api')")
            .action(PolicyAction.rateLimit().capacity(50).rate("30s").build())
        .rule("block-countries")
            .expression("conn.geo.country_code in ['XX', 'YY']")
            .action(PolicyAction.deny(403, "Blocked"))
    .onHttpResponse()
        .rule("compress")
            .action(PolicyAction.compressResponse().algorithms("gzip").build())
    .build();
```

The `build()` method returns a YAML string ready for use as a Traffic Policy document.
