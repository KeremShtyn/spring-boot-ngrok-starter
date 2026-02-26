# spring-boot-ngrok-starter — Claude Code Project Plan

## Project Overview

Build a production-ready, open-source Spring Boot starter that provides seamless ngrok integration for Java applications. The starter goes far beyond simple tunnel creation — it brings ngrok's full gateway capabilities (Traffic Policy engine, webhook auto-registration, multi-tunnel support, request inspection) into the Spring Boot ecosystem with idiomatic auto-configuration, annotation-driven APIs, and developer-first ergonomics.

**Repository name**: `spring-boot-ngrok-starter`
**Package base**: `com.kermel.ngrok`
**License**: Apache 2.0
**Java version**: 17+
**Spring Boot version**: 3.2+

**Target audience**: Java/Spring Boot developers who need to expose local services publicly for webhook testing, mobile backend development, client demos, OAuth callback testing, QA access, or local development with production-like edge features (rate limiting, JWT validation, geo-blocking, WAF).

**Key differentiators from existing solutions** (existing Spring+ngrok repos have 23-35 stars max and only do basic tunneling):
1. Traffic Policy DSL — annotation-driven Java API that generates ngrok Traffic Policy YAML
2. Webhook auto-registration — auto-register public URL with Stripe, GitHub, Slack, etc. on startup
3. Multi-tunnel support — expose multiple ports with independent configurations
4. Full Spring Boot idiom — actuator endpoints, health indicators, Spring events, profile-aware activation
5. Request inspection API — programmatic access to ngrok's captured requests

---

## Technical Foundation

### How ngrok Works

ngrok creates encrypted reverse tunnels from public URLs to your localhost. The architecture:

```
Internet → ngrok Cloud Edge → ngrok Agent (local) → Your Spring Boot App (localhost:8080)
```

Three integration layers:

1. **Secure Tunnels**: The core — expose a local port via a public URL (HTTP, TCP, TLS protocols)
2. **Traffic Policy Engine**: CEL-based rules evaluated on ngrok's cloud edge BEFORE traffic reaches your app. Supports rate limiting, JWT validation, IP restriction, geo-blocking, URL rewriting, custom responses, OWASP WAF rules, and more. Configured via YAML/JSON documents.
3. **API & Observability**: Request inspection (localhost:4040), structured event logging, ngrok API for programmatic resource management.

### ngrok Java Integration Options

There are two Java libraries for ngrok:

**Option A — `java-ngrok` wrapper (USE THIS)**
- Library: `com.github.alexdlaird:java-ngrok` (latest: 2.5.x)
- Manages the ngrok binary automatically (downloads, starts, stops)
- Wraps the ngrok CLI and its local API (localhost:4040)
- Supports all ngrok features through CLI flags
- Works on Windows, macOS, Linux — no native library hassles
- This is the recommended approach for v1 of our starter

```xml
<dependency>
    <groupId>com.github.alexdlaird</groupId>
    <artifactId>java-ngrok</artifactId>
    <version>2.5.0</version>
</dependency>
```

Key API:
```java
// Create client
NgrokClient ngrokClient = new NgrokClient.Builder().build();

// Open HTTP tunnel
Tunnel httpTunnel = ngrokClient.connect(
    new CreateTunnel.Builder()
        .withAddr(8080)
        .withBindTls(true)  // HTTPS only
        .build()
);
String publicUrl = httpTunnel.getPublicUrl();

// Open TCP tunnel
Tunnel tcpTunnel = ngrokClient.connect(
    new CreateTunnel.Builder()
        .withProto(Proto.TCP)
        .withAddr(5432)
        .build()
);

// Open tunnel with traffic policy
Tunnel policyTunnel = ngrokClient.connect(
    new CreateTunnel.Builder()
        .withAddr(8080)
        .withDomain("my-app.ngrok.dev")
        .withTrafficPolicy(trafficPolicyYamlString)
        .build()
);

// List tunnels
List<Tunnel> tunnels = ngrokClient.getTunnels();

// Disconnect
ngrokClient.disconnect(publicUrl);

// Kill ngrok process
ngrokClient.kill();
```

**Option B — ngrok Java SDK (FUTURE / v2)**
- Library: `com.ngrok:ngrok-java` + `com.ngrok:ngrok-java-native` (version 0.5.0)
- Embeds ngrok as a native library (no external binary)
- Requires platform-specific native classifier
- Better performance, but more complex build setup
- Consider for future version

### ngrok Traffic Policy System

Traffic Policy is ngrok's CEL-based rules engine. Policies are YAML/JSON documents with three phases:

- `on_tcp_connect` — When TCP connection is established (before HTTP)
- `on_http_request` — When HTTP request is received (before forwarding to app)
- `on_http_response` — When response is received from app (before sending to client)

Available actions include:
- `deny` — Block request with status code
- `rate-limit` — Sliding window rate limiting
- `jwt` — JWT token validation
- `custom-response` — Return custom response without forwarding
- `redirect` — HTTP redirect
- `add-headers` / `remove-headers` — Modify headers
- `url-rewrite` — Rewrite URL path
- `compress-response` — Gzip/deflate compression
- `log` — Structured event logging
- `forward-internal` — Forward to internal endpoint
- `owasp-crs-request` — OWASP Core Rule Set WAF

Example Traffic Policy YAML:
```yaml
on_http_request:
  - name: "Rate limit by IP"
    actions:
      - type: rate-limit
        config:
          algorithm: sliding_window
          capacity: 100
          rate: 60s
          bucket_key:
            - conn.client_ip
  - name: "Block admin from outside US"
    expressions:
      - "req.url.path.startsWith('/admin')"
      - "conn.client_ip.geo.country_code != 'US'"
    actions:
      - type: deny
        config:
          status_code: 403
on_http_response:
  - name: "Add security headers"
    actions:
      - type: add-headers
        config:
          headers:
            X-Frame-Options: DENY
            X-Content-Type-Options: nosniff
```

### ngrok Local API (Inspection)

When ngrok is running, it exposes a local API at `http://localhost:4040/api`:
- `GET /api/tunnels` — List active tunnels
- `GET /api/requests/http` — List captured HTTP requests
- `GET /api/requests/http/:id` — Get specific request details
- `POST /api/tunnels` — Create new tunnel
- `DELETE /api/tunnels/:name` — Close tunnel
- `POST /api/requests/http` — Replay a request

This API is how we'll implement the request inspection features.

---

## Project Structure

```
spring-boot-ngrok-starter/
│
├── README.md                                          # Comprehensive with badges, quick start, architecture
├── LICENSE                                            # Apache 2.0
├── pom.xml                                            # Parent POM (Maven multi-module)
├── .github/
│   └── workflows/
│       ├── ci.yml                                     # Build + test on PR
│       └── release.yml                                # Publish to Maven Central (future)
│
├── ngrok-spring-boot-autoconfigure/                   # Core auto-configuration module
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/kermel/ngrok/
│       │   │   ├── autoconfigure/
│       │   │   │   ├── NgrokAutoConfiguration.java            # Main auto-config entry point
│       │   │   │   ├── NgrokProperties.java                   # @ConfigurationProperties binding
│       │   │   │   ├── NgrokTunnelManager.java                # Lifecycle management (start/stop tunnels)
│       │   │   │   ├── NgrokCondition.java                    # Conditional activation logic
│       │   │   │   └── NgrokBannerPrinter.java                # Startup banner with public URL
│       │   │   ├── core/
│       │   │   │   ├── NgrokTunnel.java                       # Tunnel abstraction (record)
│       │   │   │   ├── NgrokTunnelRegistry.java               # Registry of all active tunnels
│       │   │   │   ├── NgrokPublicUrlProvider.java            # Bean providing public URL to other components
│       │   │   │   └── NgrokHealthIndicator.java              # Spring Boot health indicator
│       │   │   ├── event/
│       │   │   │   ├── NgrokTunnelEstablishedEvent.java       # Spring event fired when tunnel is ready
│       │   │   │   ├── NgrokTunnelClosedEvent.java            # Spring event fired when tunnel closes
│       │   │   │   └── NgrokReadyEvent.java                   # Fired when all tunnels are established
│       │   │   ├── actuator/
│       │   │   │   ├── NgrokEndpoint.java                     # /actuator/ngrok endpoint
│       │   │   │   └── NgrokEndpointAutoConfiguration.java    # Actuator auto-config
│       │   │   └── exception/
│       │   │       ├── NgrokStartupException.java
│       │   │       ├── NgrokTunnelException.java
│       │   │       └── NgrokAuthTokenMissingException.java
│       │   └── resources/
│       │       └── META-INF/
│       │           └── spring/
│       │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       └── test/
│           └── java/com/kermel/ngrok/
│               ├── autoconfigure/
│               │   ├── NgrokAutoConfigurationTest.java
│               │   ├── NgrokPropertiesTest.java
│               │   └── NgrokTunnelManagerTest.java
│               └── core/
│                   └── NgrokTunnelRegistryTest.java
│
├── ngrok-spring-boot-starter/                         # Starter module (just pulls dependencies)
│   ├── pom.xml                                        # Depends on autoconfigure + java-ngrok
│   └── src/main/resources/
│       └── META-INF/
│           └── spring/
│               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│
├── ngrok-traffic-policy-spring/                       # Traffic Policy DSL module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/kermel/ngrok/policy/
│       │   ├── annotation/
│       │   │   ├── NgrokTrafficPolicy.java                    # @NgrokTrafficPolicy class-level annotation
│       │   │   ├── OnHttpRequest.java                         # @OnHttpRequest method annotation
│       │   │   ├── OnHttpResponse.java                        # @OnHttpResponse method annotation
│       │   │   └── OnTcpConnect.java                          # @OnTcpConnect method annotation
│       │   ├── dsl/
│       │   │   ├── TrafficPolicyBuilder.java                  # Fluent API for building policies
│       │   │   ├── PolicyRule.java                            # Rule representation
│       │   │   ├── PolicyAction.java                          # Action factory (deny, rateLimit, jwt, etc.)
│       │   │   ├── RateLimitAction.java                       # Rate limit action builder
│       │   │   ├── JwtAction.java                             # JWT validation action builder
│       │   │   ├── DenyAction.java                            # Deny action builder
│       │   │   ├── CustomResponseAction.java                  # Custom response action builder
│       │   │   ├── RedirectAction.java                        # Redirect action builder
│       │   │   ├── AddHeadersAction.java                      # Add headers action builder
│       │   │   ├── RemoveHeadersAction.java                   # Remove headers action builder
│       │   │   ├── UrlRewriteAction.java                      # URL rewrite action builder
│       │   │   └── CompressResponseAction.java                # Compression action builder
│       │   ├── generator/
│       │   │   ├── TrafficPolicyGenerator.java                # Converts annotations/DSL → YAML string
│       │   │   └── TrafficPolicyYamlSerializer.java           # YAML serialization logic
│       │   └── autoconfigure/
│       │       └── TrafficPolicyAutoConfiguration.java        # Scans for @NgrokTrafficPolicy beans
│       └── test/java/com/kermel/ngrok/policy/
│           ├── dsl/TrafficPolicyBuilderTest.java
│           ├── generator/TrafficPolicyGeneratorTest.java
│           └── annotation/TrafficPolicyAnnotationTest.java
│
├── ngrok-webhook-spring/                              # Webhook auto-registration module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/kermel/ngrok/webhook/
│       │   ├── WebhookAutoRegistrar.java                      # Listens for NgrokReadyEvent, registers webhooks
│       │   ├── WebhookProperties.java                         # Webhook config properties
│       │   ├── WebhookAutoConfiguration.java                  # Auto-config for webhook module
│       │   ├── provider/
│       │   │   ├── WebhookProvider.java                       # Interface for webhook providers
│       │   │   ├── StripeWebhookProvider.java                 # Stripe webhook registration
│       │   │   ├── GitHubWebhookProvider.java                 # GitHub webhook registration
│       │   │   ├── SlackWebhookProvider.java                  # Slack webhook registration
│       │   │   ├── TwilioWebhookProvider.java                 # Twilio webhook registration
│       │   │   └── CustomWebhookProvider.java                 # Generic HTTP POST registration
│       │   └── lifecycle/
│       │       ├── WebhookRegistrationResult.java             # Registration result (success/failure/URL)
│       │       └── WebhookDeregistrar.java                    # Cleanup on shutdown
│       └── test/java/com/kermel/ngrok/webhook/
│           ├── WebhookAutoRegistrarTest.java
│           └── provider/
│               ├── StripeWebhookProviderTest.java
│               └── GitHubWebhookProviderTest.java
│
├── ngrok-inspector-spring/                            # Request inspection module
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/kermel/ngrok/inspector/
│       │   ├── NgrokInspector.java                            # Main inspection API
│       │   ├── CapturedRequest.java                           # Request data record
│       │   ├── CapturedResponse.java                          # Response data record
│       │   ├── InspectorClient.java                           # HTTP client for ngrok local API (4040)
│       │   ├── InspectorAutoConfiguration.java                # Auto-config
│       │   └── actuator/
│       │       └── NgrokRequestsEndpoint.java                 # /actuator/ngrok/requests endpoint
│       └── test/java/com/kermel/ngrok/inspector/
│           └── NgrokInspectorTest.java
│
├── samples/                                           # Example applications
│   ├── sample-basic/                                  # Minimal: just expose port 8080
│   │   ├── pom.xml
│   │   └── src/main/java/.../BasicSampleApplication.java
│   ├── sample-webhook/                                # Webhook registration with Stripe/GitHub
│   │   ├── pom.xml
│   │   └── src/main/java/.../WebhookSampleApplication.java
│   ├── sample-traffic-policy/                         # Traffic Policy DSL demo
│   │   ├── pom.xml
│   │   └── src/main/java/.../TrafficPolicySampleApplication.java
│   └── sample-multi-tunnel/                           # Multi-port exposure
│       ├── pom.xml
│       └── src/main/java/.../MultiTunnelSampleApplication.java
│
└── docs/
    ├── getting-started.md
    ├── traffic-policy.md
    ├── webhooks.md
    ├── multi-tunnel.md
    ├── inspection.md
    └── configuration-reference.md
```

---

## Module Specifications

### Module 1: `ngrok-spring-boot-autoconfigure` (Core)

This is the heart of the project. It manages ngrok lifecycle within Spring Boot.

#### NgrokProperties.java

Full `@ConfigurationProperties` binding:

```java
@ConfigurationProperties(prefix = "ngrok")
public class NgrokProperties {

    /** Master switch to enable/disable ngrok integration */
    private boolean enabled = true;

    /** ngrok auth token. Falls back to NGROK_AUTHTOKEN env var */
    private String authToken;

    /** Spring profiles in which ngrok should be active. Default: dev, local */
    private List<String> activeProfiles = List.of("dev", "local");

    /** Whether to activate only when specific profiles are active.
     *  If false, ngrok activates regardless of profile (but still respects 'enabled'). */
    private boolean profileRestricted = true;

    /** ngrok region (us, eu, ap, au, sa, jp, in) */
    private String region;

    /** Custom ngrok binary path (if not using auto-download) */
    private String binaryPath;

    /** Log level for ngrok process output */
    private String logLevel = "info";

    /** Default tunnel configuration (when no multi-tunnel config is provided) */
    private TunnelProperties defaultTunnel = new TunnelProperties();

    /** Named tunnel configurations for multi-tunnel support */
    private Map<String, TunnelProperties> tunnels = new LinkedHashMap<>();

    /** Banner configuration */
    private BannerProperties banner = new BannerProperties();

    /** Inspection API configuration */
    private InspectionProperties inspection = new InspectionProperties();

    public static class TunnelProperties {
        /** Local port to expose. Defaults to server.port */
        private Integer port;

        /** Protocol: http, tcp, tls */
        private String protocol = "http";

        /** Custom ngrok domain (e.g., my-app.ngrok.dev) — requires paid plan */
        private String domain;

        /** Bind to HTTPS only (ignore HTTP). Default: true */
        private boolean httpsOnly = true;

        /** Inline Traffic Policy YAML string */
        private String trafficPolicy;

        /** Path to Traffic Policy YAML file */
        private String trafficPolicyFile;

        /** Basic auth for the tunnel (username:password) */
        private String basicAuth;

        /** IP restrictions (allow list) */
        private List<String> allowCidrs;

        /** IP restrictions (deny list) */
        private List<String> denyCidrs;

        /** Circuit breaker — if ngrok fails to start, should the app still boot? Default: true */
        private boolean failOpen = true;

        /** Custom metadata for this tunnel */
        private String metadata;
    }

    public static class BannerProperties {
        /** Show ngrok public URL in startup banner */
        private boolean enabled = true;

        /** Copy public URL to system clipboard on startup */
        private boolean copyToClipboard = false;
    }

    public static class InspectionProperties {
        /** Enable the ngrok inspection API integration */
        private boolean enabled = true;

        /** ngrok inspection API port (default 4040) */
        private int port = 4040;
    }
}
```

#### NgrokAutoConfiguration.java

Main auto-configuration class. Must:

1. Be annotated with `@AutoConfiguration`
2. Use `@ConditionalOnProperty(prefix = "ngrok", name = "enabled", havingValue = "true", matchIfMissing = true)`
3. Use `@ConditionalOnClass(NgrokClient.class)` to only activate when java-ngrok is on classpath
4. Import `NgrokProperties` via `@EnableConfigurationProperties`
5. Create the `NgrokClient` bean with configured auth token, region, binary path
6. Create the `NgrokTunnelManager` bean that handles lifecycle
7. Create the `NgrokTunnelRegistry` bean as singleton registry of active tunnels
8. Create the `NgrokPublicUrlProvider` bean for easy URL access
9. Register a `SmartLifecycle` bean that starts tunnels after the server is ready and stops them on shutdown

**Critical lifecycle timing**: Tunnels must be created AFTER the embedded server (Tomcat/Jetty/Undertow) has started and is listening on its port. Use `SmartLifecycle` with `phase = Integer.MAX_VALUE - 1` or listen for `WebServerInitializedEvent`.

```java
@Component
public class NgrokLifecycle implements SmartLifecycle, ApplicationListener<WebServerInitializedEvent> {

    private int serverPort;
    private boolean running = false;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.serverPort = event.getWebServer().getPort();
        startTunnels();
    }

    private void startTunnels() {
        // 1. Determine tunnels to create (default single tunnel or multi-tunnel config)
        // 2. For each tunnel, call NgrokTunnelManager.createTunnel()
        // 3. Register each tunnel in NgrokTunnelRegistry
        // 4. Print banner with URLs
        // 5. Publish NgrokTunnelEstablishedEvent for each tunnel
        // 6. Publish NgrokReadyEvent when all tunnels are up
        // 7. Inject ngrok.public-url into Spring Environment for @Value usage
    }

    @Override
    public void stop() {
        // 1. Publish NgrokTunnelClosedEvent for each tunnel
        // 2. Disconnect all tunnels
        // 3. Kill ngrok process
    }
}
```

#### NgrokTunnelManager.java

Handles the actual ngrok operations. Responsibilities:
- Initialize `NgrokClient` with proper configuration
- Auth token resolution order: `ngrok.auth-token` property → `NGROK_AUTHTOKEN` env var → ngrok config file
- Create tunnels with specified parameters
- Handle tunnel creation failures (retry logic, fail-open behavior)
- Reconnection on tunnel drop (ngrok agent occasionally restarts)
- Graceful cleanup on shutdown

```java
@Component
public class NgrokTunnelManager {

    private final NgrokClient ngrokClient;
    private final NgrokProperties properties;

    public NgrokTunnel createTunnel(String name, NgrokProperties.TunnelProperties config) {
        CreateTunnel.Builder builder = new CreateTunnel.Builder()
            .withName(name)
            .withAddr(config.getPort());

        // Set protocol
        switch (config.getProtocol()) {
            case "tcp" -> builder.withProto(Proto.TCP);
            case "tls" -> builder.withProto(Proto.TLS);
            default -> {
                builder.withBindTls(config.isHttpsOnly());
            }
        }

        // Set domain if configured
        if (config.getDomain() != null) {
            builder.withDomain(config.getDomain());
        }

        // Set traffic policy if configured (from DSL, file, or inline)
        String policy = resolveTrafficPolicy(name, config);
        if (policy != null) {
            builder.withTrafficPolicy(policy);
        }

        // Set basic auth
        if (config.getBasicAuth() != null) {
            builder.withAuth(config.getBasicAuth());
        }

        Tunnel tunnel = ngrokClient.connect(builder.build());
        return new NgrokTunnel(name, tunnel.getPublicUrl(), config.getPort(), config.getProtocol());
    }
}
```

#### NgrokTunnel.java (Record)

```java
public record NgrokTunnel(
    String name,
    String publicUrl,
    int localPort,
    String protocol,
    Instant createdAt,
    String domain,
    boolean trafficPolicyEnabled
) {}
```

#### NgrokTunnelRegistry.java

Thread-safe registry of all active tunnels:

```java
@Component
public class NgrokTunnelRegistry {

    private final ConcurrentHashMap<String, NgrokTunnel> tunnels = new ConcurrentHashMap<>();

    public void register(NgrokTunnel tunnel) { ... }
    public void deregister(String name) { ... }
    public Optional<NgrokTunnel> getTunnel(String name) { ... }
    public NgrokTunnel getDefaultTunnel() { ... }  // The main HTTP tunnel
    public Collection<NgrokTunnel> getAllTunnels() { ... }
    public String getPublicUrl() { ... }  // Shortcut for default tunnel's URL
    public String getPublicUrl(String tunnelName) { ... }
}
```

#### NgrokPublicUrlProvider.java

Makes the public URL available as a Spring property:

```java
@Component
public class NgrokPublicUrlProvider implements EnvironmentPostProcessor {
    // After tunnels are created, inject ngrok.public-url into Environment
    // This enables: @Value("${ngrok.public-url}") String publicUrl;
}
```

Note: Since tunnels are created after context startup, this needs a different approach — either:
- Use `NgrokTunnelRegistry` bean injection instead of `@Value`
- Or use a lazy property source that blocks until tunnel is ready

**Recommended approach**: Provide `NgrokTunnelRegistry` as the primary bean-based access, and document that `@Value("${ngrok.public-url}")` is not available at construction time but IS available via `@EventListener(NgrokReadyEvent.class)`.

#### Spring Events

```java
public class NgrokTunnelEstablishedEvent extends ApplicationEvent {
    private final NgrokTunnel tunnel;
    // Fired for EACH tunnel when it's established
}

public class NgrokTunnelClosedEvent extends ApplicationEvent {
    private final NgrokTunnel tunnel;
    // Fired for EACH tunnel when it's closed
}

public class NgrokReadyEvent extends ApplicationEvent {
    private final Collection<NgrokTunnel> tunnels;
    // Fired ONCE when ALL tunnels are established
    // This is the event webhook module and other consumers listen for
}
```

#### NgrokHealthIndicator.java

```java
@Component
@ConditionalOnEnabledHealthIndicator("ngrok")
public class NgrokHealthIndicator extends AbstractHealthIndicator {

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        // Check if ngrok process is running
        // Check if tunnels are active
        // Include tunnel URLs in health details
        // Return UP/DOWN with tunnel info
    }
}
```

#### NgrokEndpoint.java (Actuator)

```java
@Endpoint(id = "ngrok")
public class NgrokEndpoint {

    @ReadOperation
    public Map<String, Object> ngrokInfo() {
        // Return:
        // - status: "running" / "stopped"
        // - tunnels: list of active tunnels with URLs, ports, protocols
        // - uptime: how long ngrok has been running
        // - trafficPolicy: whether traffic policy is active per tunnel
        // - inspectionUrl: http://localhost:4040
    }
}
```

#### NgrokBannerPrinter.java

Prints a clear, visible banner in the console when tunnels are established:

```
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║   🚀 ngrok tunnels established                              ║
║                                                              ║
║   Public URL:  https://abc123.ngrok-free.app                 ║
║   Forwarding:  https://abc123.ngrok-free.app → localhost:8080║
║                                                              ║
║   Inspect:     http://localhost:4040                         ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
```

For multi-tunnel:
```
╔══════════════════════════════════════════════════════════════╗
║   🚀 ngrok tunnels established                              ║
║                                                              ║
║   api       https://abc123.ngrok-free.app → localhost:8080   ║
║   frontend  https://def456.ngrok-free.app → localhost:3000   ║
║   postgres  tcp://0.tcp.ngrok.io:12345    → localhost:5432   ║
║                                                              ║
║   Inspect:  http://localhost:4040                            ║
╚══════════════════════════════════════════════════════════════╝
```

---

### Module 2: `ngrok-spring-boot-starter`

This is a thin starter module following Spring Boot conventions. It contains NO code — only dependency declarations.

Its `pom.xml` pulls in:
- `ngrok-spring-boot-autoconfigure`
- `com.github.alexdlaird:java-ngrok`
- `org.springframework.boot:spring-boot-starter`
- `org.springframework.boot:spring-boot-starter-actuator` (optional)

This is what end users add to their project:
```xml
<dependency>
    <groupId>com.kermel</groupId>
    <artifactId>ngrok-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

### Module 3: `ngrok-traffic-policy-spring`

The **killer feature** module. Provides annotation-driven and fluent DSL APIs for building ngrok Traffic Policies in Java.

#### Annotation API

**Class-level annotation**:
```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface NgrokTrafficPolicy {
    /** Optional tunnel name this policy applies to. Empty = default tunnel */
    String tunnel() default "";
}
```

**Method-level annotations**:
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnHttpRequest {
    /** CEL expression(s) that filter when this rule applies */
    String[] expressions() default {};

    /** Rule name (for readability in logs/debugging) */
    String name() default "";

    /** Execution order (lower = earlier) */
    int order() default 0;
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnHttpResponse {
    String[] expressions() default {};
    String name() default "";
    int order() default 0;
}

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnTcpConnect {
    String[] expressions() default {};
    String name() default "";
    int order() default 0;
}
```

**Usage example**:
```java
@NgrokTrafficPolicy
public class MyGatewayPolicy {

    @OnHttpRequest(
        name = "Rate limit API",
        expressions = "req.url.path.startsWith('/api')"
    )
    public PolicyAction rateLimitApi() {
        return PolicyAction.rateLimit()
            .algorithm("sliding_window")
            .capacity(100)
            .rate("60s")
            .bucketKey("conn.client_ip")
            .build();
    }

    @OnHttpRequest(
        name = "Block non-US admin access",
        expressions = {
            "req.url.path.startsWith('/admin')",
            "conn.client_ip.geo.country_code != 'US'"
        }
    )
    public PolicyAction blockNonUsAdmin() {
        return PolicyAction.deny(403, "Access denied from your region");
    }

    @OnHttpRequest(
        name = "JWT validation for API",
        expressions = "req.url.path.startsWith('/api/v2')"
    )
    public PolicyAction jwtValidation() {
        return PolicyAction.jwt()
            .issuer("https://auth.example.com")
            .audience("my-api")
            .jwksUrl("https://auth.example.com/.well-known/jwks.json")
            .build();
    }

    @OnHttpResponse(name = "Security headers")
    public PolicyAction securityHeaders() {
        return PolicyAction.addHeaders()
            .header("X-Frame-Options", "DENY")
            .header("X-Content-Type-Options", "nosniff")
            .header("Strict-Transport-Security", "max-age=31536000")
            .build();
    }

    @OnHttpResponse(name = "Compress responses")
    public PolicyAction compress() {
        return PolicyAction.compressResponse()
            .algorithms("gzip", "deflate")
            .build();
    }
}
```

#### Fluent DSL API

For programmatic / non-annotation usage:

```java
@Bean
public TrafficPolicy myPolicy() {
    return TrafficPolicy.builder()
        .onHttpRequest()
            .rule("Rate limit")
                .action(PolicyAction.rateLimit()
                    .capacity(100)
                    .rate("60s")
                    .bucketKey("conn.client_ip")
                    .build())
            .and()
            .rule("Block bots")
                .expression("'bot' in req.headers['user-agent']")
                .action(PolicyAction.deny(403))
            .and()
        .onHttpResponse()
            .rule("CORS headers")
                .action(PolicyAction.addHeaders()
                    .header("Access-Control-Allow-Origin", "*")
                    .build())
            .and()
        .build();
}
```

#### TrafficPolicyGenerator.java

The core converter. Responsibilities:
1. At startup, scan for all `@NgrokTrafficPolicy` annotated beans
2. Invoke each annotated method to collect `PolicyAction` objects
3. Group by phase (`on_http_request`, `on_http_response`, `on_tcp_connect`)
4. Sort by `order`
5. Serialize to YAML string
6. Make the YAML string available to `NgrokTunnelManager` for tunnel creation

This must run BEFORE tunnels are created. The flow is:
```
Context starts → @NgrokTrafficPolicy beans created → TrafficPolicyGenerator scans and generates YAML
→ NgrokTunnelManager reads generated YAML → Tunnels created with policy attached
```

#### PolicyAction.java (Factory)

```java
public class PolicyAction {

    // Static factory methods — entry points
    public static DenyAction deny(int statusCode) { ... }
    public static DenyAction deny(int statusCode, String message) { ... }
    public static RateLimitAction.Builder rateLimit() { ... }
    public static JwtAction.Builder jwt() { ... }
    public static CustomResponseAction.Builder customResponse() { ... }
    public static RedirectAction redirect(String url) { ... }
    public static RedirectAction redirect(String url, int statusCode) { ... }
    public static AddHeadersAction.Builder addHeaders() { ... }
    public static RemoveHeadersAction.Builder removeHeaders() { ... }
    public static UrlRewriteAction.Builder urlRewrite() { ... }
    public static CompressResponseAction.Builder compressResponse() { ... }
    public static LogAction.Builder log() { ... }

    // Internal representation
    abstract String getType();        // "deny", "rate-limit", "jwt", etc.
    abstract Map<String, Object> getConfig();  // Action config map
}
```

Each concrete action builder should validate inputs and produce a serializable config map that the YAML generator uses.

---

### Module 4: `ngrok-webhook-spring`

Automatically registers/deregisters webhook URLs with third-party services when tunnels start/stop.

#### Configuration

```yaml
ngrok:
  webhooks:
    stripe:
      enabled: true
      api-key: ${STRIPE_API_KEY}
      events:
        - payment_intent.succeeded
        - checkout.session.completed
        - invoice.paid
      path: /webhooks/stripe
      # Auto-deregister on shutdown
      auto-deregister: true

    github:
      enabled: true
      token: ${GITHUB_TOKEN}
      owner: kermel
      repo: my-app
      events:
        - push
        - pull_request
      path: /webhooks/github
      content-type: json
      auto-deregister: true

    slack:
      enabled: true
      # Slack doesn't support programmatic URL update for event subscriptions
      # but this logs the URL to copy into the Slack app dashboard
      path: /webhooks/slack
      log-only: true

    twilio:
      enabled: true
      account-sid: ${TWILIO_ACCOUNT_SID}
      auth-token: ${TWILIO_AUTH_TOKEN}
      phone-number: "+1234567890"
      path: /webhooks/twilio

    custom:
      - name: my-service
        registration-url: https://api.example.com/webhooks
        method: POST
        headers:
          Authorization: "Bearer ${MY_SERVICE_TOKEN}"
        body-template: |
          {
            "url": "{{ngrok.public-url}}/callback",
            "events": ["order.created", "order.updated"]
          }
        deregistration-url: https://api.example.com/webhooks/{{webhook.id}}
        deregistration-method: DELETE
```

#### WebhookProvider Interface

```java
public interface WebhookProvider {
    String name();
    boolean isEnabled();
    WebhookRegistrationResult register(String publicBaseUrl) throws WebhookRegistrationException;
    void deregister(WebhookRegistrationResult registration) throws WebhookRegistrationException;
}
```

#### Registration Flow

1. Listen for `NgrokReadyEvent`
2. For each enabled webhook provider:
   a. Construct the full webhook URL: `publicBaseUrl + provider.path`
   b. Call provider's `register()` method
   c. Log the registration result (success/failure/webhook URL)
   d. Store the `WebhookRegistrationResult` for later deregistration
3. On shutdown (`@PreDestroy` or `DisposableBean`):
   a. For each registered webhook with `auto-deregister: true`:
   b. Call provider's `deregister()` method
   c. Log the deregistration result

#### Stripe Provider Implementation Notes

Use Stripe's Java SDK or direct HTTP calls to the Stripe API:
- `POST /v1/webhook_endpoints` to create
- `DELETE /v1/webhook_endpoints/:id` to delete
- Store the returned webhook endpoint ID for deregistration

#### GitHub Provider Implementation Notes

Use GitHub REST API:
- `POST /repos/{owner}/{repo}/hooks` to create
- `DELETE /repos/{owner}/{repo}/hooks/{hook_id}` to delete
- Store the returned hook ID for deregistration

#### Custom Provider Implementation Notes

Uses template interpolation:
- `{{ngrok.public-url}}` → the ngrok public URL
- `{{webhook.id}}` → the ID returned from registration (for deregistration)
- Custom headers and body template support

**Important**: Use non-blocking HTTP client for registrations (WebClient or RestClient). Don't block the application startup thread for too long. Consider making webhook registration async with a configurable timeout.

---

### Module 5: `ngrok-inspector-spring`

Provides programmatic access to ngrok's request inspection API.

#### NgrokInspector.java

```java
@Component
public class NgrokInspector {

    /** Get the N most recent captured requests */
    public List<CapturedRequest> getRecentRequests(int limit) { ... }

    /** Get a specific captured request by ID */
    public Optional<CapturedRequest> getRequest(String requestId) { ... }

    /** Replay a previously captured request */
    public CapturedResponse replay(String requestId) { ... }

    /** Get requests matching a path pattern */
    public List<CapturedRequest> getRequestsByPath(String pathPattern) { ... }

    /** Clear all captured requests */
    public void clearRequests() { ... }
}
```

#### CapturedRequest.java

```java
public record CapturedRequest(
    String id,
    String method,
    String path,
    String fullUrl,
    Map<String, List<String>> headers,
    String body,
    int responseStatusCode,
    long durationMs,
    Instant timestamp
) {}
```

#### InspectorClient.java

HTTP client for ngrok's local API at `http://localhost:4040/api`. Parse JSON responses from:
- `GET /api/requests/http` → list of captured requests
- `GET /api/requests/http/:id` → single request detail
- `POST /api/requests/http` (with `id` parameter) → replay

Use `RestClient` (Spring 6.1+) or `WebClient` for the HTTP calls.

#### Actuator Endpoint

`/actuator/ngrok/requests` — Returns the last N captured requests in JSON format. Configurable limit via property.

---

## Configuration Examples

### Minimal — Just Expose Port 8080

```yaml
# application-dev.yml
ngrok:
  enabled: true
  # Auth token from NGROK_AUTHTOKEN env var (no config needed)
```

Usage: `NGROK_AUTHTOKEN=xxx ./mvnw spring-boot:run -Dspring.profiles.active=dev`

### Webhook Development

```yaml
ngrok:
  enabled: true
  auth-token: ${NGROK_AUTHTOKEN}
  default-tunnel:
    domain: my-dev.ngrok.dev
  webhooks:
    stripe:
      api-key: ${STRIPE_API_KEY}
      events: [payment_intent.succeeded, checkout.session.completed]
      path: /api/webhooks/stripe
      auto-deregister: true
```

### Multi-Service Local Development

```yaml
ngrok:
  enabled: true
  tunnels:
    api:
      port: 8080
      domain: api.my-app.ngrok.dev
    frontend:
      port: 3000
    admin:
      port: 8081
      basic-auth: admin:secret
    database:
      port: 5432
      protocol: tcp
```

### Production-Like Edge Gateway

```yaml
ngrok:
  enabled: true
  default-tunnel:
    domain: staging.my-app.ngrok.dev
    # Traffic policy via file reference
    traffic-policy-file: classpath:traffic-policy.yml
```

Or via annotation DSL (see Module 3 above).

### Full Featured

```yaml
ngrok:
  enabled: true
  auth-token: ${NGROK_AUTHTOKEN}
  region: eu
  active-profiles: [dev, local, staging]
  banner:
    enabled: true
    copy-to-clipboard: true
  inspection:
    enabled: true
  default-tunnel:
    port: 8080
    https-only: true
    domain: my-app.ngrok.dev
  webhooks:
    stripe:
      api-key: ${STRIPE_API_KEY}
      events: [payment_intent.succeeded]
      path: /webhooks/stripe
    github:
      token: ${GITHUB_TOKEN}
      owner: kermel
      repo: my-project
      events: [push, pull_request]
      path: /webhooks/github
```

---

## Implementation Phases

### Phase 1 — Project Skeleton & Core Tunnel (Priority: CRITICAL)

**Goal**: Maven multi-module project compiles. Single tunnel auto-starts on app boot. Public URL logged.

Steps:
1. Create parent POM with all module declarations, dependency management, Java 17, Spring Boot 3.2+ BOM
2. Create `ngrok-spring-boot-autoconfigure` module skeleton
3. Implement `NgrokProperties` with `@ConfigurationProperties`
4. Implement `NgrokAutoConfiguration` with conditional activation
5. Implement `NgrokTunnelManager` — create a single tunnel on startup
6. Implement `NgrokTunnel` record
7. Implement `NgrokTunnelRegistry`
8. Implement `NgrokBannerPrinter` — log the public URL clearly
9. Implement Spring event publishing (`NgrokTunnelEstablishedEvent`, `NgrokReadyEvent`)
10. Implement lifecycle management (start after WebServer, stop on shutdown)
11. Create `ngrok-spring-boot-starter` module (thin, dependency-only)
12. Create `sample-basic` — minimal Spring Boot app that uses the starter
13. Write unit tests for `NgrokProperties`, `NgrokTunnelRegistry`, `NgrokAutoConfiguration`
14. Register auto-configuration in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Deliverable**: User adds starter dependency + `NGROK_AUTHTOKEN` env var → app starts → public URL printed → accessible from internet.

### Phase 2 — Actuator, Health, & Error Handling (Priority: HIGH)

Steps:
1. Implement `NgrokHealthIndicator`
2. Implement `NgrokEndpoint` (`/actuator/ngrok`)
3. Implement `NgrokEndpointAutoConfiguration` (conditional on actuator being on classpath)
4. Implement robust error handling:
   - `NgrokAuthTokenMissingException` — clear error message if no token
   - `NgrokStartupException` — handle binary download failures, port conflicts
   - `NgrokTunnelException` — handle tunnel creation failures
   - Fail-open behavior: if `failOpen = true`, app boots without ngrok; if `false`, startup fails
5. Add reconnection logic — detect tunnel drops, reconnect automatically
6. Write tests

### Phase 3 — Multi-Tunnel Support (Priority: HIGH)

Steps:
1. Extend `NgrokTunnelManager` to create multiple tunnels from `ngrok.tunnels.*` config
2. Update `NgrokBannerPrinter` for multi-tunnel display
3. Update `NgrokTunnelRegistry` with named tunnel lookup
4. Update actuator endpoint with all tunnel details
5. Create `sample-multi-tunnel`
6. Write tests for multi-tunnel scenarios

### Phase 4 — Traffic Policy DSL (Priority: HIGH — Differentiator Feature)

Steps:
1. Create `ngrok-traffic-policy-spring` module
2. Implement annotations: `@NgrokTrafficPolicy`, `@OnHttpRequest`, `@OnHttpResponse`, `@OnTcpConnect`
3. Implement `PolicyAction` factory with all action builders:
   - `DenyAction`
   - `RateLimitAction`
   - `JwtAction`
   - `CustomResponseAction`
   - `RedirectAction`
   - `AddHeadersAction` / `RemoveHeadersAction`
   - `UrlRewriteAction`
   - `CompressResponseAction`
   - `LogAction`
4. Implement `TrafficPolicyBuilder` fluent DSL
5. Implement `TrafficPolicyGenerator` — scan annotations, collect actions, sort by order
6. Implement `TrafficPolicyYamlSerializer` — convert internal model to ngrok YAML format
7. Implement `TrafficPolicyAutoConfiguration` — wire generator into tunnel creation flow
8. Create `sample-traffic-policy`
9. Write comprehensive tests for:
   - Each action builder
   - YAML serialization correctness
   - Annotation scanning
   - Integration with tunnel creation

### Phase 5 — Webhook Auto-Registration (Priority: MEDIUM)

Steps:
1. Create `ngrok-webhook-spring` module
2. Implement `WebhookProvider` interface
3. Implement `StripeWebhookProvider` (use Stripe Java SDK or direct HTTP)
4. Implement `GitHubWebhookProvider` (use GitHub REST API)
5. Implement `SlackWebhookProvider` (log-only mode)
6. Implement `TwilioWebhookProvider`
7. Implement `CustomWebhookProvider` (template-based HTTP registration)
8. Implement `WebhookAutoRegistrar` — listens for `NgrokReadyEvent`, registers all
9. Implement `WebhookDeregistrar` — cleans up on shutdown
10. Implement `WebhookAutoConfiguration`
11. Create `sample-webhook`
12. Write tests (mock HTTP calls to Stripe/GitHub APIs)

### Phase 6 — Request Inspection (Priority: MEDIUM)

Steps:
1. Create `ngrok-inspector-spring` module
2. Implement `InspectorClient` — HTTP client for localhost:4040
3. Implement `NgrokInspector` — high-level API
4. Implement `CapturedRequest` / `CapturedResponse` records
5. Implement `NgrokRequestsEndpoint` actuator endpoint
6. Implement `InspectorAutoConfiguration`
7. Write tests

### Phase 7 — Polish & Documentation (Priority: HIGH)

Steps:
1. Comprehensive README.md with:
   - Badges (build status, Maven Central, Java version, license)
   - Architecture diagram (Mermaid)
   - Quick start (3 steps: add dependency, set env var, run)
   - Feature overview table
   - Configuration reference
   - Comparison with existing solutions
2. Per-module documentation in `docs/`
3. Javadoc on all public classes
4. GitHub Actions CI (`ci.yml`) — build + test on PR
5. Code formatting (Checkstyle or Spotless)
6. Sample applications verification — all samples must boot and work
7. `CONTRIBUTING.md`
8. Review all TODOs and edge cases

---

## Key Maven Dependencies

```xml
<!-- Parent POM dependency management -->
<dependencyManagement>
    <dependencies>
        <!-- Spring Boot BOM -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>3.4.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- Core: java-ngrok wrapper -->
<dependency>
    <groupId>com.github.alexdlaird</groupId>
    <artifactId>java-ngrok</artifactId>
    <version>2.5.0</version>
</dependency>

<!-- Spring Boot auto-configuration -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
</dependency>

<!-- Configuration properties metadata generation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>

<!-- Actuator (optional) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
    <optional>true</optional>
</dependency>

<!-- YAML serialization for Traffic Policy -->
<dependency>
    <groupId>org.yaml</groupId>
    <artifactId>snakeyaml</artifactId>
</dependency>

<!-- HTTP client for webhook registration & inspector -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <optional>true</optional>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Code Quality Standards

- **Java 17+ features**: Records for data classes, sealed interfaces where appropriate, pattern matching, text blocks for multi-line strings
- **Spring Boot conventions**: Follow official auto-configuration patterns — `@Conditional*` annotations, separate autoconfigure and starter modules, `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (NOT deprecated `spring.factories`)
- **Testing**: Unit tests for every component. Use `@SpringBootTest` with `@TestConfiguration` for integration tests. Mock `NgrokClient` in tests — don't require actual ngrok binary in CI.
- **Configuration metadata**: Use `spring-boot-configuration-processor` to generate `META-INF/spring-configuration-metadata.json` for IDE auto-completion
- **Javadoc**: All public classes and methods must have Javadoc
- **Error messages**: Every exception must have a clear, actionable error message. Example: "ngrok auth token not found. Set NGROK_AUTHTOKEN environment variable or configure ngrok.auth-token in application.yml"
- **Logging**: SLF4J with meaningful levels — DEBUG for tunnel operations, INFO for lifecycle events (tunnel established/closed), WARN for reconnection attempts, ERROR for failures
- **Thread safety**: `NgrokTunnelRegistry` must be thread-safe. Tunnel operations may be called from event listeners on different threads.
- **No blocking on startup thread**: Webhook registration should not delay application startup beyond a reasonable timeout (configurable, default 10s)

---

## Important Notes for Claude Code

### Spring Boot Auto-Configuration Pattern

This project MUST follow the modern Spring Boot 3.x auto-configuration registration pattern:

- Create file: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- List each auto-configuration class on its own line
- Do NOT use the deprecated `spring.factories` file

### java-ngrok Library Notes

- `java-ngrok` automatically downloads the ngrok binary on first use if not present
- The `NgrokClient` manages the ngrok process lifecycle
- Auth token can be set via `JavaNgrokConfig.Builder().withAuthToken(token).build()`
- The library API changed between versions — always check the 2.5.x API
- Traffic policy support was added recently — verify the `CreateTunnel.Builder` supports `.withTrafficPolicy()`
- If `withTrafficPolicy()` is not available in the version on Maven Central, fall back to writing an ngrok config file and referencing it

### Testing Strategy

- Mock `NgrokClient` in all unit tests — don't require ngrok binary in CI
- Create a `TestNgrokClient` or use Mockito to simulate tunnel creation
- For the Traffic Policy module, test YAML output against known-good YAML strings
- For the Webhook module, use WireMock or MockWebServer to simulate Stripe/GitHub APIs
- Integration tests (if any) should be in a separate profile and clearly marked as requiring ngrok

### Gotchas

1. **Port detection**: The server port is not available until the embedded server starts. Use `WebServerInitializedEvent` to get the actual port (important when `server.port=0` for random port).
2. **HTTPS tunnels**: By default, ngrok v3 creates a single HTTPS tunnel. The `bindTls` option controls this. Default should be HTTPS-only.
3. **Tunnel URL stability**: Free ngrok plans generate random URLs on each restart. Only paid plans support custom domains. The starter should handle both cases gracefully.
4. **ngrok binary download**: First run downloads ~25MB binary. Handle this gracefully — show a progress message, handle network failures.
5. **Shutdown race condition**: On app shutdown, ensure tunnels are disconnected before the Spring context fully closes. Use `SmartLifecycle` with appropriate phase ordering.
6. **Multiple app instances**: If a developer accidentally starts two instances, the second ngrok process will fail. Handle this gracefully with a clear error message.
7. **Traffic Policy YAML generation**: The YAML must be valid ngrok Traffic Policy format. Test generated YAML carefully. Use SnakeYAML's `DumperOptions` with block flow style for readability.
8. **Configuration property prefix**: Use `ngrok` as the prefix (not `spring.ngrok`) since this is a third-party integration, not a core Spring feature.
