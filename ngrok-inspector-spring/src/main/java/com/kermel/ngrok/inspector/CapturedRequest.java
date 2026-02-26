package com.kermel.ngrok.inspector;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Represents a captured HTTP request from ngrok's inspection API.
 *
 * @param id               unique request identifier (e.g., "req_abc123")
 * @param tunnelName       name of the tunnel that received the request
 * @param remoteAddr       client IP and port (e.g., "1.2.3.4:12345")
 * @param method           HTTP method (GET, POST, PUT, DELETE, etc.)
 * @param path             request path (e.g., "/api/users")
 * @param fullUrl          complete URL including protocol and host
 * @param requestHeaders   HTTP request headers
 * @param requestBody      request body (null for bodyless methods)
 * @param responseStatusCode HTTP response status code
 * @param responseHeaders  HTTP response headers
 * @param responseBody     response body
 * @param durationMs       request processing duration in milliseconds
 * @param timestamp        when the request was captured
 */
public record CapturedRequest(
        String id,
        String tunnelName,
        String remoteAddr,
        String method,
        String path,
        String fullUrl,
        Map<String, List<String>> requestHeaders,
        String requestBody,
        int responseStatusCode,
        Map<String, List<String>> responseHeaders,
        String responseBody,
        long durationMs,
        Instant timestamp
) {}
