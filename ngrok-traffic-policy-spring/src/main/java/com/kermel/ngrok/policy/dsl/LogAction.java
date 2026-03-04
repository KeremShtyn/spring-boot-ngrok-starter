package com.kermel.ngrok.policy.dsl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Log action — structured event logging.
 */
public class LogAction extends PolicyAction {

    private final Map<String, Object> metadata;

    private LogAction(Builder builder) {
        this.metadata = Map.copyOf(builder.metadata);
    }

    @Override
    public String getType() {
        return "log";
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        if (!metadata.isEmpty()) {
            config.put("metadata", new LinkedHashMap<>(metadata));
        }
        return config;
    }

    public static class Builder {
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public LogAction build() {
            return new LogAction(this);
        }
    }
}
