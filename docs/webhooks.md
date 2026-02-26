# Webhook Auto-Registration

Automatically register your ngrok public URL with external webhook services when tunnels start. On application shutdown, webhooks with `auto-deregister: true` are cleaned up.

## Setup

```xml
<dependency>
    <groupId>com.kermel</groupId>
    <artifactId>ngrok-webhook-spring</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Providers

### Stripe

Registers a [Stripe Webhook Endpoint](https://stripe.com/docs/webhooks) via the Stripe API:

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
      path: /webhooks/stripe         # local endpoint path
      auto-deregister: true          # clean up on shutdown
```

The provider creates a webhook endpoint pointing to `{ngrok-url}/webhooks/stripe` and deletes it on shutdown.

### GitHub

Registers a [GitHub Repository Webhook](https://docs.github.com/en/webhooks) via the GitHub API:

```yaml
ngrok:
  webhooks:
    github:
      enabled: true
      token: ${GITHUB_TOKEN}         # Personal access token
      owner: kermel                  # Repository owner
      repo: my-app                   # Repository name
      events:
        - push
        - pull_request
      path: /webhooks/github
      content-type: json             # json or form (default: json)
      auto-deregister: true
```

### Slack

Slack does not support programmatic URL updates. The Slack provider operates in **log-only mode** — it logs your ngrok URL so you can manually configure it in the [Slack App settings](https://api.slack.com/apps):

```yaml
ngrok:
  webhooks:
    slack:
      enabled: true
      path: /webhooks/slack
```

On startup, you'll see:

```
INFO  Slack webhook URL (configure manually): https://abc123.ngrok.io/webhooks/slack
```

### Twilio

Updates the SMS webhook URL on a [Twilio phone number](https://www.twilio.com/docs/usage/webhooks):

```yaml
ngrok:
  webhooks:
    twilio:
      enabled: true
      account-sid: ${TWILIO_ACCOUNT_SID}
      auth-token: ${TWILIO_AUTH_TOKEN}
      phone-number: "+1234567890"
      path: /webhooks/twilio
      auto-deregister: true
```

### Custom Provider

Register webhooks with any HTTP service using a template-based approach:

```yaml
ngrok:
  webhooks:
    custom:
      - name: order-service
        registration-url: https://api.example.com/webhooks
        method: POST                                           # default: POST
        body-template: '{"url": "{{ngrok.public-url}}", "events": ["order.created"]}'
        headers:
          Authorization: "Bearer ${API_TOKEN}"
          Content-Type: "application/json"
        deregistration-url: https://api.example.com/webhooks/{{webhook.id}}
        deregistration-method: DELETE
        auto-deregister: true
```

#### Template Variables

| Variable | Description |
|----------|-------------|
| `{{ngrok.public-url}}` | Full ngrok public URL with path |
| `{{webhook.id}}` | Webhook ID returned during registration |

#### Response Parsing

The custom provider tries to extract a webhook ID from the registration response by looking for common field names: `id`, `webhook_id`, `hookId`.

## Multi-Tunnel Support

Webhook providers can target a specific named tunnel:

```yaml
ngrok:
  tunnels:
    api:
      port: 8080
    admin:
      port: 8080

  webhooks:
    stripe:
      enabled: true
      api-key: ${STRIPE_API_KEY}
      path: /webhooks/stripe
      tunnel: api              # Use the "api" tunnel's public URL
```

## Error Handling

Registration failures are logged as warnings but do not prevent application startup (fail-open behavior). This means:

- If Stripe API is unreachable, the app still boots
- If a GitHub token is invalid, other webhooks still register
- Failed registrations are not retried automatically

Check logs for `WARN` messages from `WebhookAutoRegistrar`.

## Programmatic Access

```java
@Autowired
WebhookAutoRegistrar registrar;

// Get all registration results
List<WebhookRegistrationResult> results = registrar.getRegistrations();

results.forEach(r -> {
    if (r.success()) {
        log.info("{}: registered as {} at {}",
            r.provider(), r.webhookId(), r.webhookUrl());
    } else {
        log.warn("{}: failed — {}", r.provider(), r.message());
    }
});
```
