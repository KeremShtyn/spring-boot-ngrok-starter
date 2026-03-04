package com.kermel.ngrok.policy.dsl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Add-headers action — adds HTTP headers to the request or response.
 */
public class AddHeadersAction extends PolicyAction {

    private final Map<String, String> headers;

    private AddHeadersAction(Builder builder) {
        this.headers = Map.copyOf(builder.headers);
    }

    @Override
    public String getType() {
        return "add-headers";
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("headers", new LinkedHashMap<>(headers));
        return config;
    }

    public static class Builder {
        private final Map<String, String> headers = new LinkedHashMap<>();

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public AddHeadersAction build() {
            return new AddHeadersAction(this);
        }
    }
}
