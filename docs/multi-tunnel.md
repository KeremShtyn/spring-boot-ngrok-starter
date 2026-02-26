# Multi-Tunnel Configuration

Expose multiple local services through separate ngrok tunnels, each with independent configuration.

## Setup

No additional dependency needed — multi-tunnel support is built into the core starter.

## Configuration

Define named tunnels under `ngrok.tunnels`:

```yaml
ngrok:
  tunnels:
    api:
      port: 8080
      protocol: http
      metadata: "Public API"

    admin:
      port: 8080
      protocol: http
      basic-auth: "admin:secret123"
      metadata: "Admin panel"

    database:
      port: 5432
      protocol: tcp
      fail-open: true
      metadata: "PostgreSQL access"
```

Each tunnel gets its own public URL and can have different:
- Ports and protocols
- Basic auth credentials
- Traffic policies
- Fail-open behavior
- IP restrictions

## Accessing Tunnel Information

### Via NgrokTunnelRegistry

```java
@Autowired
NgrokTunnelRegistry registry;

// Get a specific tunnel
Optional<NgrokTunnel> api = registry.getTunnel("api");
api.ifPresent(t -> log.info("API URL: {}", t.publicUrl()));

// Get all tunnels
Collection<NgrokTunnel> all = registry.getAllTunnels();

// Get the default tunnel
Optional<NgrokTunnel> defaultTunnel = registry.getDefaultTunnel();
```

### Via Spring Events

```java
@EventListener
public void onTunnelReady(NgrokTunnelEstablishedEvent event) {
    NgrokTunnel tunnel = event.getTunnel();
    log.info("Tunnel '{}' at {} (port {})",
        tunnel.name(), tunnel.publicUrl(), tunnel.localPort());
}

@EventListener
public void onAllReady(NgrokReadyEvent event) {
    log.info("{} tunnels established", event.getTunnels().size());
}
```

## Per-Tunnel Properties

Each tunnel supports the full set of `TunnelProperties`:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `port` | `int` | server.port | Local port to expose |
| `protocol` | `string` | `http` | Protocol: `http`, `tcp`, `tls` |
| `domain` | `string` | — | Custom ngrok domain (paid plan) |
| `https-only` | `boolean` | `true` | Bind HTTPS only |
| `basic-auth` | `string` | — | Basic auth (`user:pass`) |
| `traffic-policy` | `string` | — | Inline Traffic Policy YAML |
| `traffic-policy-file` | `string` | — | Path to Traffic Policy file |
| `allow-cidrs` | `list` | — | IP allowlist |
| `deny-cidrs` | `list` | — | IP denylist |
| `fail-open` | `boolean` | `true` | Continue if tunnel fails |
| `metadata` | `string` | — | Custom metadata |

## Partial Failure

With `fail-open: true` (default), individual tunnel failures don't prevent other tunnels from starting. The app boots successfully with whatever tunnels could be established.

```yaml
ngrok:
  tunnels:
    critical-api:
      port: 8080
      fail-open: false    # App WILL NOT start if this fails

    debug-tunnel:
      port: 9090
      fail-open: true     # App continues if this fails
```

## Banner Output

The startup banner shows all established tunnels:

```
┌──────────────────────────────────────────────────────┐
│  ngrok tunnels established!                          │
├──────────────────────────────────────────────────────┤
│  api      → https://abc123.ngrok.io    (port 8080)  │
│  admin    → https://def456.ngrok.io    (port 8080)  │
│  database → tcp://ghi789.ngrok.io:1234 (port 5432)  │
│                                                      │
│  Inspect: http://localhost:4040                      │
└──────────────────────────────────────────────────────┘
```
