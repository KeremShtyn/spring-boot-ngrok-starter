package com.kermel.ngrok.exception;

/**
 * Thrown when ngrok fails to start (e.g., binary download failure, port conflict).
 */
public class NgrokStartupException extends NgrokTunnelException {

    public NgrokStartupException(String message) {
        super(message);
    }

    public NgrokStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
