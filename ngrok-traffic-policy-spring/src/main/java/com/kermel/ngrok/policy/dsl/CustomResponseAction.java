package com.kermel.ngrok.policy.dsl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom response action — returns a custom response without forwarding to the backend.
 */
public class CustomResponseAction extends PolicyAction {

    private final int statusCode;
    private final String content;
    private final Map<String, String> headers;

    private CustomResponseAction(Builder builder) {
        this.statusCode = builder.statusCode;
        this.content = builder.content;
        this.headers = Map.copyOf(builder.headers);
    }

    @Override
    public String getType() {
        return "custom-response";
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("status_code", statusCode);
        if (content != null) {
            config.put("content", content);
        }
        if (!headers.isEmpty()) {
            config.put("headers", new LinkedHashMap<>(headers));
        }
        return config;
    }

    public static class Builder {
        private int statusCode = 200;
        private String content;
        private final Map<String, String> headers = new LinkedHashMap<>();

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public CustomResponseAction build() {
            return new CustomResponseAction(this);
        }
    }
}
