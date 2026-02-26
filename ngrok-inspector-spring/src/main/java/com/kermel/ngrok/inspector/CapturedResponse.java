package com.kermel.ngrok.inspector;

import java.util.List;
import java.util.Map;

/**
 * Represents the response from replaying a captured request via ngrok's inspection API.
 *
 * @param statusCode  HTTP response status code
 * @param headers     HTTP response headers
 * @param body        response body
 * @param durationMs  processing duration in milliseconds
 */
public record CapturedResponse(
        int statusCode,
        Map<String, List<String>> headers,
        String body,
        long durationMs
) {}
