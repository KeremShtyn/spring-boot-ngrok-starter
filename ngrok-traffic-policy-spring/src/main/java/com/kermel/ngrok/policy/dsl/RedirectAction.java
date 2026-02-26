package com.kermel.ngrok.policy.dsl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Redirect action — sends an HTTP redirect response.
 */
public class RedirectAction extends PolicyAction {

    private final String url;
    private final int statusCode;

    public RedirectAction(String url, int statusCode) {
        this.url = url;
        this.statusCode = statusCode;
    }

    @Override
    public String getType() {
        return "redirect";
    }

    @Override
    public Map<String, Object> getConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("to", url);
        config.put("status_code", statusCode);
        return config;
    }
}
