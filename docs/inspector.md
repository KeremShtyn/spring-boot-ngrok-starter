# Request Inspection

Programmatic access to ngrok's [request inspection API](https://ngrok.com/docs/agent/api/), allowing you to capture, query, filter, and replay HTTP requests that flow through ngrok tunnels.

## Setup

```xml
<dependency>
    <groupId>com.kermel</groupId>
    <artifactId>ngrok-inspector-spring</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Configuration

```yaml
ngrok:
  inspection:
    enabled: true    # default: true
    port: 4040       # ngrok inspection API port (default: 4040)
```

## NgrokInspector API

The `NgrokInspector` bean is auto-configured when the module is present:

```java
@Autowired
NgrokInspector inspector;
```

### Recent Requests

```java
// Get 20 most recent requests
List<CapturedRequest> recent = inspector.getRecentRequests(20);

// Get with default limit (50)
List<CapturedRequest> recent = inspector.getRecentRequests();
```

### Get by ID

```java
Optional<CapturedRequest> request = inspector.getRequest("req_abc123");
request.ifPresent(r -> {
    log.info("{} {} -> {}", r.method(), r.path(), r.responseStatusCode());
});
```

### Filter by Path

```java
// Regex pattern matching
List<CapturedRequest> apiRequests = inspector.getRequestsByPath("/api/.*");
```

### Filter by Method

```java
List<CapturedRequest> posts = inspector.getRequestsByMethod("POST");
```

### Filter by Status Code

```java
// Client errors
List<CapturedRequest> clientErrors = inspector.getRequestsByStatus(400, 499);

// All errors (4xx + 5xx)
List<CapturedRequest> errors = inspector.getErrorRequests();
```

### Replay Requests

Replay a previously captured request through the tunnel:

```java
Optional<CapturedResponse> response = inspector.replay("req_abc123");
response.ifPresent(r -> {
    log.info("Replayed: {} ({}ms)", r.statusCode(), r.durationMs());
});

// Replay through a specific tunnel
Optional<CapturedResponse> response = inspector.replay("req_abc123", "api");
```

### Clear and Check Availability

```java
inspector.clearRequests();

boolean available = inspector.isAvailable();
```

## CapturedRequest Record

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Unique request ID (e.g., `req_abc123`) |
| `tunnelName` | `String` | Tunnel that received the request |
| `remoteAddr` | `String` | Client IP and port |
| `method` | `String` | HTTP method |
| `path` | `String` | Request path |
| `fullUrl` | `String` | Full URL with host |
| `requestHeaders` | `Map<String, List<String>>` | Request headers |
| `requestBody` | `String` | Request body |
| `responseStatusCode` | `int` | Response status code |
| `responseHeaders` | `Map<String, List<String>>` | Response headers |
| `responseBody` | `String` | Response body |
| `durationMs` | `long` | Processing time in ms |
| `timestamp` | `Instant` | When the request was captured |

## CapturedResponse Record

| Field | Type | Description |
|-------|------|-------------|
| `statusCode` | `int` | HTTP status code |
| `headers` | `Map<String, List<String>>` | Response headers |
| `body` | `String` | Response body |
| `durationMs` | `long` | Processing time in ms |

## Actuator Endpoint

When Spring Boot Actuator is on the classpath, an endpoint is exposed at `/actuator/ngrokrequests`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: ngrokrequests
```

### List Requests

```
GET /actuator/ngrokrequests?limit=20
```

Response:

```json
{
  "available": true,
  "limit": 20,
  "count": 5,
  "requests": [
    {
      "id": "req_001",
      "method": "POST",
      "path": "/api/orders",
      "responseStatusCode": 201,
      "durationMs": 45,
      "timestamp": "2025-01-15T10:30:00Z"
    }
  ]
}
```

### Get Request Detail

```
GET /actuator/ngrokrequests/req_001
```

Returns the full request/response data including headers and body.

### Replay Request

```
POST /actuator/ngrokrequests
Content-Type: application/json

{ "id": "req_001" }
```

## InspectorClient

For low-level access, inject `InspectorClient` directly. It communicates with the ngrok agent's local API at `http://localhost:4040/api`:

```java
@Autowired
InspectorClient client;

List<CapturedRequest> all = client.listRequests(100);
Optional<CapturedRequest> one = client.getRequest("req_abc");
Optional<CapturedResponse> replayed = client.replayRequest("req_abc", "default");
client.clearRequests();
boolean up = client.isAvailable();
```
