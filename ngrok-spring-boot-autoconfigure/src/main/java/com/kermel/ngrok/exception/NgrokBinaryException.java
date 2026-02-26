package com.kermel.ngrok.exception;

/**
 * Thrown when the ngrok binary cannot be found, downloaded, or executed.
 */
public class NgrokBinaryException extends NgrokStartupException {

    public NgrokBinaryException(String message) {
        super(message);
    }

    public NgrokBinaryException(String message, Throwable cause) {
        super(message, cause);
    }
}
