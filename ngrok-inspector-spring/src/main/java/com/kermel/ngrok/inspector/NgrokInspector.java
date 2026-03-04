package com.kermel.ngrok.inspector;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level API for inspecting HTTP requests captured by ngrok.
 *
 * <p>Provides convenient methods for querying, filtering, and replaying
 * requests that flow through ngrok tunnels. Delegates to {@link InspectorClient}
 * for communication with ngrok's local inspection API.
 *
 * <p>Example usage:
 * <pre>{@code
 * @Autowired
 * NgrokInspector inspector;
 *
 * // Get recent requests
 * List<CapturedRequest> recent = inspector.getRecentRequests(20);
 *
 * // Filter by path
 * List<CapturedRequest> apiRequests = inspector.getRequestsByPath("/api/.*");
 *
 * // Replay a request
 * Optional<CapturedResponse> response = inspector.replay("req_abc123");
 * }</pre>
 */
public class NgrokInspector {

    private static final Logger log = LoggerFactory.getLogger(NgrokInspector.class);
    private static final int DEFAULT_LIMIT = 50;

    private final InspectorClient client;

    public NgrokInspector(InspectorClient client) {
        this.client = client;
    }

    /**
     * Retrieves the N most recent captured requests.
     *
     * @param limit maximum number of requests to return
     * @return list of captured requests, newest first; empty list if API unavailable
     */
    public List<CapturedRequest> getRecentRequests(int limit) {
        return client.listRequests(Math.max(1, limit));
    }

    /**
     * Retrieves the most recent captured requests using the default limit (50).
     *
     * @return list of captured requests, newest first
     */
    public List<CapturedRequest> getRecentRequests() {
        return getRecentRequests(DEFAULT_LIMIT);
    }

    /**
     * Retrieves a specific captured request by its ID.
     *
     * @param requestId the request identifier (e.g., "req_abc123")
     * @return the captured request, or empty if not found
     */
    public Optional<CapturedRequest> getRequest(String requestId) {
        return client.getRequest(requestId);
    }

    /**
     * Replays a previously captured request through the tunnel.
     *
     * @param requestId the request identifier to replay
     * @return the response from the replayed request, or empty if replay failed
     */
    public Optional<CapturedResponse> replay(String requestId) {
        return replay(requestId, null);
    }

    /**
     * Replays a previously captured request through a specific tunnel.
     *
     * @param requestId  the request identifier to replay
     * @param tunnelName the tunnel name to replay through (null for default)
     * @return the response from the replayed request, or empty if replay failed
     */
    public Optional<CapturedResponse> replay(String requestId, String tunnelName) {
        return client.replayRequest(requestId, tunnelName);
    }

    /**
     * Retrieves requests whose path matches a regex pattern.
     *
     * @param pathPattern regex pattern to match against request paths (e.g., "/api/.*")
     * @return list of matching captured requests
     */
    public List<CapturedRequest> getRequestsByPath(String pathPattern) {
        return getRequestsByPath(pathPattern, DEFAULT_LIMIT);
    }

    /**
     * Retrieves requests whose path matches a regex pattern, up to the given limit.
     *
     * @param pathPattern regex pattern to match against request paths
     * @param limit       maximum number of requests to scan from the API
     * @return list of matching captured requests
     */
    public List<CapturedRequest> getRequestsByPath(String pathPattern, int limit) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(pathPattern);
        } catch (PatternSyntaxException e) {
            log.warn("Invalid regex pattern '{}': {}", pathPattern, e.getMessage());
            return Collections.emptyList();
        }
        return client.listRequests(limit).stream()
                .filter(req -> pattern.matcher(req.path()).matches())
                .toList();
    }

    /**
     * Retrieves requests filtered by HTTP method.
     *
     * @param method HTTP method to filter by (e.g., "GET", "POST")
     * @return list of matching captured requests
     */
    public List<CapturedRequest> getRequestsByMethod(String method) {
        return client.listRequests(DEFAULT_LIMIT).stream()
                .filter(req -> method.equalsIgnoreCase(req.method()))
                .toList();
    }

    /**
     * Retrieves requests filtered by response status code range.
     *
     * @param minStatus minimum status code (inclusive)
     * @param maxStatus maximum status code (inclusive)
     * @return list of matching captured requests
     */
    public List<CapturedRequest> getRequestsByStatus(int minStatus, int maxStatus) {
        return client.listRequests(DEFAULT_LIMIT).stream()
                .filter(req -> req.responseStatusCode() >= minStatus && req.responseStatusCode() <= maxStatus)
                .toList();
    }

    /**
     * Retrieves all requests that resulted in an error response (4xx or 5xx).
     *
     * @return list of captured requests with error responses
     */
    public List<CapturedRequest> getErrorRequests() {
        return getRequestsByStatus(400, 599);
    }

    /**
     * Clears all captured requests from ngrok's inspection buffer.
     */
    public void clearRequests() {
        client.clearRequests();
    }

    /**
     * Checks whether the ngrok inspection API is reachable.
     *
     * @return true if the API responds successfully
     */
    public boolean isAvailable() {
        return client.isAvailable();
    }
}
