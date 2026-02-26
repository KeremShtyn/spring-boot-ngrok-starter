package com.kermel.ngrok.sample.webhook;

import com.kermel.ngrok.event.NgrokReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.EventListener;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Sample application demonstrating webhook auto-registration.
 *
 * <p>When ngrok tunnels start, webhooks are automatically registered
 * with the configured providers (Stripe, GitHub, custom services).
 * When the app shuts down, webhooks with {@code auto-deregister: true}
 * are automatically cleaned up.
 *
 * <p>Run with:
 * <pre>{@code
 * NGROK_AUTHTOKEN=xxx \
 * STRIPE_API_KEY=sk_test_xxx \
 * GITHUB_TOKEN=ghp_xxx \
 * mvn spring-boot:run -Dspring.profiles.active=dev
 * }</pre>
 */
@SpringBootApplication
@RestController
public class WebhookSampleApplication {

    private static final Logger log = LoggerFactory.getLogger(WebhookSampleApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(WebhookSampleApplication.class, args);
    }

    @PostMapping("/webhooks/stripe")
    public Map<String, String> stripeWebhook(@RequestBody String payload) {
        log.info("Received Stripe webhook: {} bytes", payload.length());
        return Map.of("status", "received");
    }

    @PostMapping("/webhooks/github")
    public Map<String, String> githubWebhook(@RequestBody String payload) {
        log.info("Received GitHub webhook: {} bytes", payload.length());
        return Map.of("status", "received");
    }

    @PostMapping("/webhooks/slack")
    public Map<String, String> slackWebhook(@RequestBody String payload) {
        log.info("Received Slack event: {} bytes", payload.length());
        return Map.of("status", "received");
    }

    @PostMapping("/callbacks/orders")
    public Map<String, String> orderCallback(@RequestBody String payload) {
        log.info("Received order callback: {} bytes", payload.length());
        return Map.of("status", "received");
    }

    @EventListener
    public void onNgrokReady(NgrokReadyEvent event) {
        event.getTunnels().forEach(tunnel ->
                log.info("ngrok tunnel ready: {}", tunnel.publicUrl()));
    }
}
