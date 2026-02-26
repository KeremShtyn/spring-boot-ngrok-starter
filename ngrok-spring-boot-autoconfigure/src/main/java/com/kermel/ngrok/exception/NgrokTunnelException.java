package com.kermel.ngrok.exception;

/**
 * Thrown when a tunnel fails to be created or encounters an error.
 */
public class NgrokTunnelException extends RuntimeException {

    public NgrokTunnelException(String message) {
        super(message);
    }

    public NgrokTunnelException(String message, Throwable cause) {
        super(message, cause);
    }
}
