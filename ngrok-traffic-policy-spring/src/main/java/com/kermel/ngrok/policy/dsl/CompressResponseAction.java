package com.kermel.ngrok.policy.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compress-response action — enables response compression.
 */
public class CompressResponseAction extends PolicyAction {

    private final List<String> algorithms;

    private CompressResponseAction(Builder builder) {
        this.algorithms = List.copyOf(builder.algorithms);
    }

    @Override
    public String getType() {
        return "compress-response";
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        if (!algorithms.isEmpty()) {
            config.put("algorithms", new ArrayList<>(algorithms));
        }
        return config;
    }

    public static class Builder {
        private final List<String> algorithms = new ArrayList<>();

        public Builder algorithms(String... algorithms) {
            for (String alg : algorithms) {
                this.algorithms.add(alg);
            }
            return this;
        }

        public CompressResponseAction build() {
            return new CompressResponseAction(this);
        }
    }
}
