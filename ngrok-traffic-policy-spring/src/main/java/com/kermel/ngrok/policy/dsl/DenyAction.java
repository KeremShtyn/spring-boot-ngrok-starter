package com.kermel.ngrok.policy.dsl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deny action — blocks the request with a status code and optional message.
 */
public class DenyAction extends PolicyAction {

    private final int statusCode;
    private final String message;

    public DenyAction(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    @Override
    public String getType() {
        return "deny";
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("status_code", statusCode);
        if (message != null) {
            config.put("content", message);
        }
        return config;
    }
}
