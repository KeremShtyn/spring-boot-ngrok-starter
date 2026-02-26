package com.kermel.ngrok.policy.dsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT validation action — validates JWT tokens on incoming requests.
 */
public class JwtAction extends PolicyAction {

    private final String issuer;
    private final String audience;
    private final String jwksUrl;
    private final List<String> allowedAlgorithms;
    private final String tokenLocation;

    private JwtAction(Builder builder) {
        this.issuer = builder.issuer;
        this.audience = builder.audience;
        this.jwksUrl = builder.jwksUrl;
        this.allowedAlgorithms = builder.allowedAlgorithms;
        this.tokenLocation = builder.tokenLocation;
    }

    @Override
    public String getType() {
        return "jwt-validation";
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();

        Map<String, Object> issuerConfig = new LinkedHashMap<>();
        if (issuer != null) {
            issuerConfig.put("value", issuer);
        }
        if (audience != null) {
            issuerConfig.put("allow_list", List.of(Map.of("value", audience)));
        }
        if (jwksUrl != null) {
            Map<String, Object> jws = new LinkedHashMap<>();
            jws.put("keys", List.of(Map.of("sources", List.of(Map.of("additional_jkus", List.of(jwksUrl))))));
            if (!allowedAlgorithms.isEmpty()) {
                jws.put("allowed_algorithms", allowedAlgorithms);
            }
            issuerConfig.put("jws", jws);
        }

        config.put("issuer", issuerConfig);

        if (tokenLocation != null) {
            config.put("token", Map.of("location", tokenLocation));
        }

        return config;
    }

    public static class Builder {
        private String issuer;
        private String audience;
        private String jwksUrl;
        private final List<String> allowedAlgorithms = new ArrayList<>();
        private String tokenLocation;

        public Builder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Builder jwksUrl(String jwksUrl) {
            this.jwksUrl = jwksUrl;
            return this;
        }

        public Builder allowedAlgorithm(String algorithm) {
            this.allowedAlgorithms.add(algorithm);
            return this;
        }

        public Builder tokenLocation(String location) {
            this.tokenLocation = location;
            return this;
        }

        public JwtAction build() {
            return new JwtAction(this);
        }
    }
}
