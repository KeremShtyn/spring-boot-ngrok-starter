package com.kermel.ngrok.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for automatic webhook registration.
 *
 * <p>Bind using the {@code ngrok.webhooks} prefix:
 * <pre>{@code
 * ngrok:
 *   webhooks:
 *     stripe:
 *       enabled: true
 *       api-key: ${STRIPE_API_KEY}
 *       events: [payment_intent.succeeded]
 *       path: /webhooks/stripe
 * }</pre>
 */
@ConfigurationProperties(prefix = "ngrok.webhooks")
public class WebhookProperties {

    private StripeWebhookConfig stripe = new StripeWebhookConfig();
    private GitHubWebhookConfig github = new GitHubWebhookConfig();
    private SlackWebhookConfig slack = new SlackWebhookConfig();
    private TwilioWebhookConfig twilio = new TwilioWebhookConfig();
    private List<CustomWebhookConfig> custom = new ArrayList<>();

    // --- Getters and Setters ---

    public StripeWebhookConfig getStripe() {
        return stripe;
    }

    public void setStripe(StripeWebhookConfig stripe) {
        this.stripe = stripe;
    }

    public GitHubWebhookConfig getGithub() {
        return github;
    }

    public void setGithub(GitHubWebhookConfig github) {
        this.github = github;
    }

    public SlackWebhookConfig getSlack() {
        return slack;
    }

    public void setSlack(SlackWebhookConfig slack) {
        this.slack = slack;
    }

    public TwilioWebhookConfig getTwilio() {
        return twilio;
    }

    public void setTwilio(TwilioWebhookConfig twilio) {
        this.twilio = twilio;
    }

    public List<CustomWebhookConfig> getCustom() {
        return custom;
    }

    public void setCustom(List<CustomWebhookConfig> custom) {
        this.custom = custom;
    }

    // --- Nested Config Classes ---

    public static class StripeWebhookConfig {
        private boolean enabled = false;
        private String apiKey;
        private List<String> events = new ArrayList<>();
        private String path = "/webhooks/stripe";
        private boolean autoDeregister = true;
        private String tunnel = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public List<String> getEvents() { return events; }
        public void setEvents(List<String> events) { this.events = events; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public boolean isAutoDeregister() { return autoDeregister; }
        public void setAutoDeregister(boolean autoDeregister) { this.autoDeregister = autoDeregister; }
        public String getTunnel() { return tunnel; }
        public void setTunnel(String tunnel) { this.tunnel = tunnel; }
    }

    public static class GitHubWebhookConfig {
        private boolean enabled = false;
        private String token;
        private String owner;
        private String repo;
        private List<String> events = new ArrayList<>();
        private String path = "/webhooks/github";
        private String contentType = "json";
        private boolean autoDeregister = true;
        private String tunnel = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        public String getOwner() { return owner; }
        public void setOwner(String owner) { this.owner = owner; }
        public String getRepo() { return repo; }
        public void setRepo(String repo) { this.repo = repo; }
        public List<String> getEvents() { return events; }
        public void setEvents(List<String> events) { this.events = events; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }
        public boolean isAutoDeregister() { return autoDeregister; }
        public void setAutoDeregister(boolean autoDeregister) { this.autoDeregister = autoDeregister; }
        public String getTunnel() { return tunnel; }
        public void setTunnel(String tunnel) { this.tunnel = tunnel; }
    }

    public static class SlackWebhookConfig {
        private boolean enabled = false;
        private String path = "/webhooks/slack";
        private boolean autoDeregister = false;
        private String tunnel = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public boolean isAutoDeregister() { return autoDeregister; }
        public void setAutoDeregister(boolean autoDeregister) { this.autoDeregister = autoDeregister; }
        public String getTunnel() { return tunnel; }
        public void setTunnel(String tunnel) { this.tunnel = tunnel; }
    }

    public static class TwilioWebhookConfig {
        private boolean enabled = false;
        private String accountSid;
        private String authToken;
        private String phoneNumber;
        private String path = "/webhooks/twilio";
        private boolean autoDeregister = true;
        private String tunnel = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAccountSid() { return accountSid; }
        public void setAccountSid(String accountSid) { this.accountSid = accountSid; }
        public String getAuthToken() { return authToken; }
        public void setAuthToken(String authToken) { this.authToken = authToken; }
        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public boolean isAutoDeregister() { return autoDeregister; }
        public void setAutoDeregister(boolean autoDeregister) { this.autoDeregister = autoDeregister; }
        public String getTunnel() { return tunnel; }
        public void setTunnel(String tunnel) { this.tunnel = tunnel; }
    }

    public static class CustomWebhookConfig {
        private String name;
        private String registrationUrl;
        private String method = "POST";
        private Map<String, String> headers = new LinkedHashMap<>();
        private String bodyTemplate;
        private String deregistrationUrl;
        private String deregistrationMethod = "DELETE";
        private boolean autoDeregister = false;
        private String tunnel = "";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRegistrationUrl() { return registrationUrl; }
        public void setRegistrationUrl(String registrationUrl) { this.registrationUrl = registrationUrl; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        public String getBodyTemplate() { return bodyTemplate; }
        public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }
        public String getDeregistrationUrl() { return deregistrationUrl; }
        public void setDeregistrationUrl(String deregistrationUrl) { this.deregistrationUrl = deregistrationUrl; }
        public String getDeregistrationMethod() { return deregistrationMethod; }
        public void setDeregistrationMethod(String deregistrationMethod) { this.deregistrationMethod = deregistrationMethod; }
        public boolean isAutoDeregister() { return autoDeregister; }
        public void setAutoDeregister(boolean autoDeregister) { this.autoDeregister = autoDeregister; }
        public String getTunnel() { return tunnel; }
        public void setTunnel(String tunnel) { this.tunnel = tunnel; }
    }
}
