package com.kermel.ngrok.webhook.provider;

import com.kermel.ngrok.webhook.WebhookProperties;
import com.kermel.ngrok.webhook.WebhookRegistrationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Webhook provider for Twilio.
 *
 * <p>Updates the SMS webhook URL for a phone number using the Twilio REST API:
 * {@code POST /2010-04-01/Accounts/{AccountSid}/IncomingPhoneNumbers/{PhoneNumberSid}.json}
 *
 * <p>Since looking up the PhoneNumberSid by number requires an extra API call,
 * this provider first lists incoming phone numbers to find the SID, then updates the URL.
 */
public class TwilioWebhookProvider implements WebhookProvider {

    private static final Logger log = LoggerFactory.getLogger(TwilioWebhookProvider.class);
    private static final String TWILIO_API_BASE = "https://api.twilio.com";

    private final WebhookProperties.TwilioWebhookConfig config;
    private final RestClient restClient;

    public TwilioWebhookProvider(WebhookProperties.TwilioWebhookConfig config) {
        this.config = config;
        if (config.getAccountSid() == null || config.getAccountSid().isBlank()) {
            throw new IllegalArgumentException(
                    "Twilio account SID must be configured. Set ngrok.webhooks.twilio.account-sid in your application properties.");
        }
        if (config.getAuthToken() == null || config.getAuthToken().isBlank()) {
            throw new IllegalArgumentException(
                    "Twilio auth token must be configured. Set ngrok.webhooks.twilio.auth-token in your application properties.");
        }
        String credentials = config.getAccountSid() + ":" + config.getAuthToken();
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.restClient = RestClient.builder()
                .baseUrl(TWILIO_API_BASE)
                .defaultHeader("Authorization", "Basic " + encoded)
                .build();
    }

    // Visible for testing
    TwilioWebhookProvider(WebhookProperties.TwilioWebhookConfig config, RestClient restClient) {
        this.config = config;
        this.restClient = restClient;
    }

    @Override
    public String name() {
        return "twilio";
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public String path() {
        return config.getPath();
    }

    @Override
    public String tunnel() {
        return config.getTunnel();
    }

    @Override
    @SuppressWarnings("unchecked")
    public WebhookRegistrationResult register(String webhookUrl) {
        try {
            // Step 1: Find the PhoneNumberSid for the configured phone number
            String sid = lookupPhoneNumberSid();

            // Step 2: Update the SMS URL for this phone number
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("SmsUrl", webhookUrl);

            restClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/IncomingPhoneNumbers/{pnSid}.json",
                            config.getAccountSid(), sid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            log.info("Twilio webhook registered for {}: {}", config.getPhoneNumber(), webhookUrl);
            return WebhookRegistrationResult.success("twilio", sid, webhookUrl, config.isAutoDeregister());

        } catch (Exception e) {
            log.error("Failed to register Twilio webhook for {}: {}", config.getPhoneNumber(), e.getMessage());
            throw new WebhookRegistrationException("Twilio webhook registration failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deregister(WebhookRegistrationResult registration) {
        String sid = registration.webhookId();
        if (sid == null) {
            log.debug("No phone number SID to reset for Twilio");
            return;
        }

        try {
            // Reset the SmsUrl to empty
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("SmsUrl", "");

            restClient.post()
                    .uri("/2010-04-01/Accounts/{sid}/IncomingPhoneNumbers/{pnSid}.json",
                            config.getAccountSid(), sid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            log.info("Twilio webhook deregistered for {}", config.getPhoneNumber());
        } catch (Exception e) {
            log.warn("Failed to deregister Twilio webhook: {}", e.getMessage());
            throw new WebhookRegistrationException("Twilio webhook deregistration failed: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private String lookupPhoneNumberSid() {
        Map<String, Object> response = restClient.get()
                .uri("/2010-04-01/Accounts/{sid}/IncomingPhoneNumbers.json?PhoneNumber={phone}",
                        config.getAccountSid(), config.getPhoneNumber())
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new WebhookRegistrationException("Empty response from Twilio when looking up phone number");
        }

        var phoneNumbers = (java.util.List<Map<String, Object>>) response.get("incoming_phone_numbers");
        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            throw new WebhookRegistrationException(
                    "Phone number " + config.getPhoneNumber() + " not found in Twilio account");
        }

        String sid = (String) phoneNumbers.get(0).get("sid");
        if (sid == null || sid.isBlank()) {
            throw new WebhookRegistrationException(
                    "Twilio returned a phone number entry without a valid SID for " + config.getPhoneNumber());
        }
        return sid;
    }
}
