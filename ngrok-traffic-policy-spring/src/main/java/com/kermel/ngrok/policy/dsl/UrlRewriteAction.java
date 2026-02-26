package com.kermel.ngrok.policy.dsl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * URL-rewrite action — rewrites the URL path before forwarding.
 */
public class UrlRewriteAction extends PolicyAction {

    private final String from;
    private final String to;

    private UrlRewriteAction(Builder builder) {
        this.from = builder.from;
        this.to = builder.to;
    }

    @Override
    public String getType() {
        return "url-rewrite";
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("from", from);
        config.put("to", to);
        return config;
    }

    public static class Builder {
        private String from;
        private String to;

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder to(String to) {
            this.to = to;
            return this;
        }

        public UrlRewriteAction build() {
            return new UrlRewriteAction(this);
        }
    }
}
