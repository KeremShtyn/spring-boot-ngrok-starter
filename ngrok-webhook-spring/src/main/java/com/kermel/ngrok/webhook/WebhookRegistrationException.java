package com.kermel.ngrok.webhook;

/**
 * Thrown when webhook registration or deregistration fails.
 */
public class WebhookRegistrationException extends RuntimeException {

    public WebhookRegistrationException(String message) {
        super(message);
    }

    public WebhookRegistrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
