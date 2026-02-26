package com.kermel.ngrok.exception;

/**
 * Thrown when ngrok fails because another ngrok process is already running
 * or there is a port conflict on the inspection API port (default 4040).
 */
public class NgrokPortConflictException extends NgrokStartupException {

    private final int port;

    public NgrokPortConflictException(int port) {
        super("ngrok port conflict: another ngrok process may already be running. " +
                "Port " + port + " is in use. Stop the existing ngrok process or change the inspection port " +
                "via ngrok.inspection.port in application.yml");
        this.port = port;
    }

    public NgrokPortConflictException(int port, Throwable cause) {
        super("ngrok port conflict: another ngrok process may already be running. " +
                "Port " + port + " is in use. Stop the existing ngrok process or change the inspection port " +
                "via ngrok.inspection.port in application.yml", cause);
        this.port = port;
    }

    public int getPort() {
        return port;
    }
}
