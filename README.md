# Spring Boot ngrok Starter

[![Build](https://github.com/kermel/spring-boot-ngrok-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/kermel/spring-boot-ngrok-starter/actions/workflows/ci.yml)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue)](https://openjdk.org/)
[![Spring Boot 3.2+](https://img.shields.io/badge/Spring%20Boot-3.2%2B-brightgreen)](https://spring.io/projects/spring-boot)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

A production-ready Spring Boot starter that provides seamless [ngrok](https://ngrok.com) integration for Java applications. Goes far beyond simple tunnel creation — it brings ngrok's full gateway capabilities into the Spring Boot ecosystem with idiomatic auto-configuration, annotation-driven APIs, and developer-first ergonomics.

## Features

| Feature | Module | Description |
|---------|--------|-------------|
| Auto-tunnel | `ngrok-spring-boot-starter` | Automatically starts ngrok tunnels on app boot |
| Multi-tunnel | `ngrok-spring-boot-starter` | Expose multiple ports with independent configs |
| Traffic Policy DSL | `ngrok-traffic-policy-spring` | Annotation-driven Java API for ngrok Traffic Policy |
| Webhook Auto-registration | `ngrok-webhook-spring` | Auto-register URLs with Stripe, GitHub, Slack, Twilio |
| Request Inspection | `ngrok-inspector-spring` | Programmatic access to ngrok's captured requests |
| Actuator & Health | `ngrok-spring-boot-starter` | `/actuator/ngrok`, health indicator, Spring events |
| Auto-reconnection | `ngrok-spring-boot-starter` | Exponential backoff tunnel recovery |

## Architecture

```
                                        ┌─────────────────────────┐
                                        │    Your Spring Boot     │
                                        │      Application        │
                                        └───────────┬─────────────┘
                                                    │
                    ┌───────────────────────────────┼───────────────────────────────┐
                    │                               │                               │
        ┌───────────▼───────────┐   ┌───────────────▼──────────┐   ┌───────────────▼──────────┐
        │  ngrok-spring-boot-   │   │  ngrok-traffic-policy-   │   │    ngrok-webhook-        │
        │    autoconfigure      │   │        spring            │   │       spring              │
        │                       │   │                          │   │                           │
        │  - NgrokLifecycle     │   │  - @OnHttpRequest        │   │  - StripeProvider         │
        │  - TunnelRegistry     │   │  - @OnHttpResponse       │   │  - GitHubProvider         │
        │  - BannerPrinter      │   │  - PolicyAction DSL      │   │  - SlackProvider          │
        │  - Reconnector        │   │  - YAML generation       │   │  - TwilioProvider         │
        │  - Health + Actuator  │   │                          │   │  - CustomProvider          │
        └───────────┬───────────┘   └──────────────────────────┘   └───────────────────────────┘
                    │
        ┌───────────▼───────────┐
        │  ngrok-inspector-     │
        │       spring          │
        │                       │
        │  - InspectorClient    │
        │  - NgrokInspector     │
        │  - Actuator endpoint  │
        └───────────────────────┘
                    │
                    ▼
        ┌───────────────────────┐
        │    ngrok Agent        │
        │  (localhost:4040)     │
        └───────────┬───────────┘
                    │
                    ▼
        ┌───────────────────────┐
        │   ngrok Cloud Edge    │ ◄── Traffic Policy rules evaluated here
        └───────────┬───────────┘
                    │
                    ▼
              [ Internet ]
```

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>com.kermel</groupId>
    <artifactId>ngrok-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Set your ngrok auth token

```bash
export NGROK_AUTHTOKEN=your_token_here
```

Or in `application.yml`:

```yaml
ngrok:
  auth-token: ${NGROK_AUTHTOKEN}
```

### 3. Run your app

```bash
mvn spring-boot:run
```

ngrok starts automatically and prints your public URL:

```
┌──────────────────────────────────────────────┐
│  ngrok tunnel established!                   │
│  Public URL: https://abc123.ngrok.io         │
│  Forwarding: https://abc123.ngrok.io → 8080  │
│  Inspect:    http://localhost:4040            │
└──────────────────────────────────────────────┘
```

## Configuration Reference

### Core Properties

```yaml
ngrok:
  enabled: true                        # Master switch (default: true)
  auth-token: ${NGROK_AUTHTOKEN}       # Auth token (or use env var)
  profile-restricted: true             # Only activate in specific profiles
  active-profiles: [dev, local]        # Profiles to activate in
  region: us                           # Region: us, eu, ap, au, sa, jp, in
  binary-path: /usr/local/bin/ngrok    # Custom ngrok binary path
  log-level: info                      # Log level for ngrok process

  # Default tunnel
  default-tunnel:
    port: 8080                         # Local port (defaults to server.port)
    protocol: http                     # Protocol: http, tcp, tls
    domain: my-app.ngrok.dev           # Custom domain (paid plan)
    https-only: true                   # Bind HTTPS only
    basic-auth: "user:pass"            # Basic auth protection
    fail-open: true                    # App boots even if ngrok fails
    traffic-policy: |                  # Inline Traffic Policy YAML
      on_http_request:
        - actions:
          - type: rate-limit
            config:
              capacity: 100
              rate: 60s

  # Multiple tunnels
  tunnels:
    api:
      port: 8080
      protocol: http
    admin:
      port: 8080
      basic-auth: "admin:secret"
    database:
      port: 5432
      protocol: tcp
      fail-open: true

  # Banner
  banner:
    enabled: true
    copy-to-clipboard: false

  # Inspection API
  inspection:
    enabled: true
    port: 4040

  # Auto-reconnection
  reconnection:
    enabled: true
    check-interval-seconds: 30
    max-attempts: 5
    initial-delay-seconds: 2
    backoff-multiplier: 2.0
    max-delay-seconds: 60
```

### Webhook Properties

```yaml
ngrok:
  webhooks:
    stripe:
      enabled: true
      api-key: ${STRIPE_API_KEY}
      events: [payment_intent.succeeded, checkout.session.completed]
      path: /webhooks/stripe
      auto-deregister: true

    github:
      enabled: true
      token: ${GITHUB_TOKEN}
      owner: kermel
      repo: my-app
      events: [push, pull_request]
      path: /webhooks/github
      auto-deregister: true

    slack:
      enabled: true
      path: /webhooks/slack

    twilio:
      enabled: true
      account-sid: ${TWILIO_ACCOUNT_SID}
      auth-token: ${TWILIO_AUTH_TOKEN}
      phone-number: "+1234567890"
      path: /webhooks/twilio

    custom:
      - name: order-service
        registration-url: https://api.example.com/webhooks
        body-template: '{"url": "{{ngrok.public-url}}"}'
        deregistration-url: https://api.example.com/webhooks/{{webhook.id}}
        deregistration-method: DELETE
        auto-deregister: true
        headers:
          Authorization: "Bearer ${API_TOKEN}"
```

## Modules

### Traffic Policy DSL

Add `ngrok-traffic-policy-spring` to use the annotation-driven Traffic Policy API:

```xml
<dependency>
    <groupId>com.kermel</groupId>
    <artifactId>ngrok-traffic-policy-spring</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Define policies as Spring components:

```java
@NgrokTrafficPolicy
public class ApiGatewayPolicy {

    @OnHttpRequest(name = "rate-limit-api", expressions = "req.url.path.startsWith('/api')")
    public PolicyAction rateLimitApi() {
        return PolicyAction.rateLimit()
                .capacity(100)
                .rate("60s")
                .bucketKey("conn.client_ip")
                .build();
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

See [docs/traffic-policy.md](docs/traffic-policy.md) for the full DSL reference.

### Webhook Auto-Registration

Add `ngrok-webhook-spring` for automatic webhook registration:

```xml
<dependency>
    <groupId>com.kermel</groupId>
    <artifactId>ngrok-webhook-spring</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

When ngrok tunnels start, webhooks are automatically registered with configured services. On shutdown, webhooks with `auto-deregister: true` are cleaned up.

See [docs/webhooks.md](docs/webhooks.md) for provider configuration details.

### Request Inspection

Add `ngrok-inspector-spring` to programmatically inspect captured requests:

```xml
<dependency>
    <groupId>com.kermel</groupId>
    <artifactId>ngrok-inspector-spring</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
@Autowired
NgrokInspector inspector;

// Get recent requests
List<CapturedRequest> recent = inspector.getRecentRequests(20);

// Filter by path
List<CapturedRequest> apiRequests = inspector.getRequestsByPath("/api/.*");

// Get error requests (4xx/5xx)
List<CapturedRequest> errors = inspector.getErrorRequests();

// Replay a request
Optional<CapturedResponse> response = inspector.replay("req_abc123");
```

Also exposes `/actuator/ngrokrequests` for HTTP access.

See [docs/inspector.md](docs/inspector.md) for the full API reference.

## Spring Events

The starter publishes Spring application events you can listen to:

```java
@EventListener
public void onTunnelEstablished(NgrokTunnelEstablishedEvent event) {
    log.info("Tunnel '{}' established: {}", event.getTunnel().name(), event.getTunnel().publicUrl());
}

@EventListener
public void onTunnelClosed(NgrokTunnelClosedEvent event) {
    log.info("Tunnel '{}' closed", event.getTunnel().name());
}

@EventListener
public void onAllTunnelsReady(NgrokReadyEvent event) {
    log.info("All {} tunnels are ready", event.getTunnels().size());
}
```

## Actuator Endpoints

When Spring Boot Actuator is on the classpath:

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/ngrok` | Tunnel status, uptime, reconnection stats |
| `GET /actuator/health/ngrok` | Health indicator |
| `GET /actuator/ngrokrequests` | Captured request list (inspector module) |
| `GET /actuator/ngrokrequests/{id}` | Captured request detail |
| `POST /actuator/ngrokrequests` | Replay a captured request |

## Sample Applications

| Sample | Port | Description |
|--------|------|-------------|
| `sample-basic` | 8080 | Minimal setup — single tunnel with banner |
| `sample-traffic-policy` | 8081 | Traffic Policy DSL with rate limiting, headers, WAF |
| `sample-multi-tunnel` | 8080 | Three tunnels: API, admin (basic auth), database (TCP) |
| `sample-webhook` | 8082 | Stripe + GitHub + Slack + custom webhook registration |
| `sample-inspector` | 8082 | Request inspection, filtering, and replay |

Run any sample:

```bash
cd samples/sample-basic
mvn spring-boot:run
```

## Requirements

- Java 17+
- Spring Boot 3.2+
- ngrok account (free tier works, [sign up here](https://ngrok.com))
- `NGROK_AUTHTOKEN` environment variable (get from [ngrok dashboard](https://dashboard.ngrok.com/get-started/your-authtoken))

## Building from Source

```bash
git clone https://github.com/kermel/spring-boot-ngrok-starter.git
cd spring-boot-ngrok-starter
mvn clean install
```

## License

This project is licensed under the [Apache License 2.0](LICENSE).
