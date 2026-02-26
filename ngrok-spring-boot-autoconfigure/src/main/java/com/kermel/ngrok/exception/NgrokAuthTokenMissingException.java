package com.kermel.ngrok.exception;

/**
 * Thrown when no ngrok auth token is found.
 */
public class NgrokAuthTokenMissingException extends NgrokStartupException {

    public NgrokAuthTokenMissingException() {
        super("ngrok auth token not found. Set the NGROK_AUTHTOKEN environment variable " +
                "or configure ngrok.auth-token in application.yml. " +
                "Get your token at https://dashboard.ngrok.com/get-started/your-authtoken");
    }
}
