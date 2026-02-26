package com.kermel.ngrok.policy.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rate-limit action — sliding window rate limiting.
 */
public class RateLimitAction extends PolicyAction {

    private final String algorithm;
    private final int capacity;
    private final String rate;
    private final List<String> bucketKeys;

    private RateLimitAction(Builder builder) {
        this.algorithm = builder.algorithm;
        this.capacity = builder.capacity;
        this.rate = builder.rate;
        this.bucketKeys = builder.bucketKeys;
    }

    @Override
    public String getType() {
        return "rate-limit";
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("algorithm", algorithm);
        config.put("capacity", capacity);
        config.put("rate", rate);
        if (!bucketKeys.isEmpty()) {
            config.put("bucket_key", bucketKeys);
        }
        return config;
    }

    public static class Builder {
        private String algorithm = "sliding_window";
        private int capacity = 100;
        private String rate = "60s";
        private final List<String> bucketKeys = new ArrayList<>();

        public Builder algorithm(String algorithm) {
            this.algorithm = algorithm;
            return this;
        }

        public Builder capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder rate(String rate) {
            this.rate = rate;
            return this;
        }

        public Builder bucketKey(String... keys) {
            for (String key : keys) {
                this.bucketKeys.add(key);
            }
            return this;
        }

        public RateLimitAction build() {
            return new RateLimitAction(this);
        }
    }
}
