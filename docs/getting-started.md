# Getting Started

## Prerequisites

- Java 17 or later
- Spring Boot 3.2 or later
- An ngrok account ([sign up free](https://ngrok.com))
- Your ngrok auth token ([get it here](https://dashboard.ngrok.com/get-started/your-authtoken))

## Installation

Add the starter to your `pom.xml`:

```xml
<dependency>
    <groupId>com.kermel</groupId>
    <artifactId>ngrok-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Configuration

### Auth Token

Set your auth token via environment variable (recommended):

```bash
export NGROK_AUTHTOKEN=your_token_here
```

Or in `application.yml`:

```yaml
ngrok:
  auth-token: ${NGROK_AUTHTOKEN}
```

### Profile Restriction

By default, ngrok only activates when `dev` or `local` Spring profiles are active. This prevents accidentally running ngrok in production:

```yaml
ngrok:
  profile-restricted: true        # default: true
  active-profiles: [dev, local]   # default: [dev, local]
```

To disable profile restriction (activate in all profiles):

```yaml
ngrok:
  profile-restricted: false
```

### Custom Domain

With a paid ngrok plan, use a stable domain:

```yaml
ngrok:
  default-tunnel:
    domain: my-app.ngrok.dev
```

### Fail-Open Behavior

By default, if ngrok fails to start, your application continues booting normally:

```yaml
ngrok:
  default-tunnel:
    fail-open: true   # default: true
```

Set `fail-open: false` to fail the application startup if ngrok cannot connect.

## Spring Events

Listen for tunnel lifecycle events:

| Event | When |
|-------|------|
| `NgrokTunnelEstablishedEvent` | A single tunnel is established |
| `NgrokTunnelClosedEvent` | A single tunnel is closed |
| `NgrokReadyEvent` | All tunnels are established |

```java
@EventListener
public void onReady(NgrokReadyEvent event) {
    event.getTunnels().forEach(tunnel ->
        log.info("{} -> {}", tunnel.publicUrl(), tunnel.localPort()));
}
```

## Actuator Integration

Add Spring Boot Actuator to expose ngrok endpoints:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,ngrok
```

- `GET /actuator/ngrok` — tunnel status, uptime, reconnection stats
- `GET /actuator/health/ngrok` — health indicator

## Auto-Reconnection

Tunnels automatically recover from disconnections:

```yaml
ngrok:
  reconnection:
    enabled: true                    # default: true
    check-interval-seconds: 30       # how often to check
    max-attempts: 5                  # 0 = unlimited
    initial-delay-seconds: 2
    backoff-multiplier: 2.0
    max-delay-seconds: 60
```

## Next Steps

- [Multi-Tunnel Configuration](multi-tunnel.md)
- [Traffic Policy DSL](traffic-policy.md)
- [Webhook Auto-Registration](webhooks.md)
- [Request Inspection](inspector.md)
