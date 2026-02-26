package com.kermel.ngrok.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the runtime version of the ngrok Spring Boot starter.
 *
 * <p>The version is read from {@code ngrok-version.properties} on the classpath,
 * which is populated by Maven resource filtering at build time.
 */
public final class NgrokStarterVersion {

    private static final String VERSION;

    static {
        String v = "unknown";
        try (InputStream is = NgrokStarterVersion.class.getClassLoader()
                .getResourceAsStream("ngrok-version.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                String value = props.getProperty("ngrok-starter.version");
                if (value != null && !value.isBlank()) {
                    v = value;
                }
            }
        } catch (IOException ignored) {
        }
        VERSION = v;
    }

    private NgrokStarterVersion() {
    }

    /**
     * Returns the version of the ngrok Spring Boot starter,
     * or {@code "unknown"} if it could not be determined.
     */
    public static String getVersion() {
        return VERSION;
    }
}
