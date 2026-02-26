package com.kermel.ngrok.policy.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remove-headers action — removes HTTP headers from the request or response.
 */
public class RemoveHeadersAction extends PolicyAction {

    private final List<String> headers;

    private RemoveHeadersAction(Builder builder) {
        this.headers = builder.headers;
    }

    @Override
    public String getType() {
        return "remove-headers";
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("headers", new ArrayList<>(headers));
        return config;
    }

    public static class Builder {
        private final List<String> headers = new ArrayList<>();

        public Builder header(String name) {
            this.headers.add(name);
            return this;
        }

        public RemoveHeadersAction build() {
            return new RemoveHeadersAction(this);
        }
    }
}
