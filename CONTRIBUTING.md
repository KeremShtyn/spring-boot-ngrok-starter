# Contributing to Spring Boot ngrok Starter

Thank you for your interest in contributing! This guide will help you get started.

## Development Setup

### Prerequisites

- Java 17+
- Maven 3.8+
- ngrok account with auth token (for integration testing)

### Building

```bash
git clone https://github.com/kermel/spring-boot-ngrok-starter.git
cd spring-boot-ngrok-starter
mvn clean install
```

### Running Tests

```bash
# All tests
mvn test

# Single module
mvn test -pl ngrok-spring-boot-autoconfigure

# Specific test class
mvn test -pl ngrok-spring-boot-autoconfigure -Dtest=NgrokAutoConfigurationTest
```

## Project Structure

```
spring-boot-ngrok-starter/
├── ngrok-spring-boot-autoconfigure/   # Core auto-configuration
├── ngrok-spring-boot-starter/         # Starter dependency (POM only)
├── ngrok-traffic-policy-spring/       # Traffic Policy DSL
├── ngrok-webhook-spring/              # Webhook auto-registration
├── ngrok-inspector-spring/            # Request inspection API
├── samples/                           # Sample applications
│   ├── sample-basic/
│   ├── sample-traffic-policy/
│   ├── sample-multi-tunnel/
│   ├── sample-webhook/
│   └── sample-inspector/
└── docs/                              # Per-module documentation
```

## Code Conventions

### General

- **Java 17+**: Use records for data classes, text blocks for multi-line strings
- **Spring Boot idiom**: `@Conditional*` annotations, `AutoConfiguration.imports` (not `spring.factories`)
- **Thread safety**: Concurrent data structures where needed (e.g., `ConcurrentHashMap`)
- **No blocking startup**: Webhook registration and other async operations should not delay app boot

### Naming

- Configuration properties: `ngrok.*` prefix, kebab-case in YAML
- Event classes: `Ngrok*Event` (e.g., `NgrokTunnelEstablishedEvent`)
- Exception classes: `Ngrok*Exception` with clear, actionable messages
- Test classes: `*Test` suffix, `@DisplayName` annotation

### Testing

- Unit tests for every component
- `ApplicationContextRunner` for auto-configuration tests
- `MockRestServiceServer` for HTTP client tests
- Mockito for unit tests with external dependencies
- No real ngrok binary required in CI — mock `NgrokClient`

### Error Messages

Every exception must have a clear, actionable message:

```java
// Good
throw new NgrokAuthTokenMissingException(
    "ngrok auth token not found. Set NGROK_AUTHTOKEN environment variable " +
    "or configure ngrok.auth-token in application.yml");

// Bad
throw new RuntimeException("Auth token missing");
```

### Logging

- `DEBUG` — tunnel operations, internal state
- `INFO` — lifecycle events (tunnel established/closed)
- `WARN` — reconnection attempts, non-fatal errors
- `ERROR` — failures that prevent functionality

## Making Changes

### Workflow

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes
4. Add tests for new functionality
5. Run `mvn clean verify` to ensure everything passes
6. Submit a pull request

### Commit Messages

Use clear, descriptive commit messages:

```
Add Twilio webhook provider support

Implements TwilioWebhookProvider that registers SMS webhook URLs
via the Twilio REST API. Supports auto-deregistration on shutdown.
```

### Pull Request Guidelines

- Keep PRs focused on a single change
- Include tests for new features
- Update documentation if adding new configuration properties
- Reference any related issues

## Adding a New Webhook Provider

1. Create a config class in `WebhookProperties` (nested static class)
2. Implement `WebhookProvider` interface
3. Add conditional bean in `WebhookAutoConfiguration`
4. Add tests (unit + auto-config)
5. Update `docs/webhooks.md`

## Adding a New Traffic Policy Action

1. Create an action class extending `PolicyAction` in `dsl/`
2. Add a static factory method to `PolicyAction`
3. Add serialization handling in `TrafficPolicyYamlSerializer`
4. Add tests
5. Update `docs/traffic-policy.md`

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
