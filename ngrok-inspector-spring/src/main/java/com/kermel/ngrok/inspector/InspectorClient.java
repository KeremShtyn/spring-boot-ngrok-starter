package com.kermel.ngrok.inspector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.*;

/**
 * Low-level HTTP client for ngrok's local inspection API at {@code http://localhost:4040/api}.
 *
 * <p>Wraps REST calls to the ngrok agent's captured request endpoints:
 * <ul>
 *   <li>{@code GET /api/requests/http} — list captured requests</li>
 *   <li>{@code GET /api/requests/http/:id} — retrieve a single request</li>
 *   <li>{@code POST /api/requests/http} — replay a captured request</li>
 *   <li>{@code DELETE /api/requests/http} — clear all captured requests</li>
 * </ul>
 */
public class InspectorClient {

    private static final Logger log = LoggerFactory.getLogger(InspectorClient.class);

    private static final int REPLAY_POLL_ATTEMPTS = 10;
    private static final long REPLAY_POLL_DELAY_MS = 200;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public InspectorClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches the most recent captured requests.
     *
     * @param limit maximum number of requests to return
     * @return list of captured requests, newest first
     */
    public List<CapturedRequest> listRequests(int limit) {
        try {
            String json = restClient.get()
                    .uri("/api/requests/http?limit={limit}", limit)
                    .retrieve()
                    .body(String.class);

            return parseRequestList(json);
        } catch (RestClientException e) {
            log.warn("Failed to fetch captured requests from ngrok inspection API: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Fetches a single captured request by its ID.
     *
     * @param requestId the request identifier
     * @return the captured request, or empty if not found
     */
    public Optional<CapturedRequest> getRequest(String requestId) {
        try {
            String json = restClient.get()
                    .uri("/api/requests/http/{id}", requestId)
                    .retrieve()
                    .body(String.class);

            return Optional.ofNullable(parseSingleRequest(json));
        } catch (RestClientException e) {
            log.warn("Failed to fetch request '{}' from ngrok inspection API: {}", requestId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Replays a previously captured request through the tunnel.
     *
     * @param requestId  the request identifier to replay
     * @param tunnelName the tunnel name to replay through
     * @return the response from the replayed request, or empty if replay failed
     */
    public Optional<CapturedResponse> replayRequest(String requestId, String tunnelName) {
        try {
            // Capture the most recent request ID before replay so we can detect the new one
            List<CapturedRequest> before = listRequests(1);
            String lastIdBeforeReplay = before.isEmpty() ? null : before.get(0).id();

            Map<String, String> body = new LinkedHashMap<>();
            body.put("id", requestId);
            if (tunnelName != null && !tunnelName.isEmpty()) {
                body.put("tunnel_name", tunnelName);
            }

            restClient.post()
                    .uri("/api/requests/http")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            // Poll until a new captured request appears that wasn't there before the replay
            for (int i = 0; i < REPLAY_POLL_ATTEMPTS; i++) {
                try {
                    Thread.sleep(REPLAY_POLL_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Optional.empty();
                }

                List<CapturedRequest> after = listRequests(1);
                if (!after.isEmpty()) {
                    CapturedRequest latest = after.get(0);
                    // Only accept a request that appeared after we triggered the replay
                    if (!Objects.equals(latest.id(), lastIdBeforeReplay)) {
                        return Optional.of(new CapturedResponse(
                                latest.responseStatusCode(),
                                latest.responseHeaders(),
                                latest.responseBody(),
                                latest.durationMs()
                        ));
                    }
                }
            }

            log.warn("Timed out waiting for replayed request '{}' to appear in captured requests", requestId);
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Failed to replay request '{}': {}", requestId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Clears all captured requests from ngrok's inspection buffer.
     */
    public void clearRequests() {
        try {
            restClient.delete()
                    .uri("/api/requests/http")
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Cleared all captured requests from ngrok inspection API");
        } catch (RestClientException e) {
            log.warn("Failed to clear captured requests: {}", e.getMessage());
        }
    }

    /**
     * Checks whether the ngrok inspection API is reachable.
     *
     * @return true if the API responds successfully
     */
    public boolean isAvailable() {
        try {
            restClient.get()
                    .uri("/api/requests/http?limit=0")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            return false;
        }
    }

    // --- JSON Parsing ---

    List<CapturedRequest> parseRequestList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode requestsNode = root.path("requests");
            if (!requestsNode.isArray()) {
                return List.of();
            }

            List<CapturedRequest> results = new ArrayList<>();
            for (JsonNode node : requestsNode) {
                CapturedRequest request = mapToCapturedRequest(node);
                if (request != null) {
                    results.add(request);
                }
            }
            return Collections.unmodifiableList(results);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse ngrok inspection API response: {}", e.getMessage());
            return List.of();
        }
    }

    CapturedRequest parseSingleRequest(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            return mapToCapturedRequest(node);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse ngrok request response: {}", e.getMessage());
            return null;
        }
    }

    private CapturedRequest mapToCapturedRequest(JsonNode node) {
        try {
            String id = node.path("id").asText(null);
            String tunnelName = node.path("tunnel_name").asText("");
            String remoteAddr = node.path("remote_addr").asText("");

            // Duration is in nanoseconds in the ngrok API
            long durationNanos = node.path("duration").asLong(0);
            long durationMs = durationNanos / 1_000_000;

            // Parse timestamp
            Instant timestamp = parseTimestamp(node.path("start").asText(null));

            // Parse request section
            JsonNode reqNode = node.path("request");
            String method = reqNode.path("method").asText("");
            String path = reqNode.path("uri").asText("");
            Map<String, List<String>> requestHeaders = parseHeaders(reqNode.path("headers"));
            String requestBody = reqNode.path("raw").asText("");

            // Build full URL from headers or use path
            String host = "";
            if (requestHeaders.containsKey("Host") && !requestHeaders.get("Host").isEmpty()) {
                host = requestHeaders.get("Host").get(0);
            }
            String fullUrl = host.isEmpty() ? path : "https://" + host + path;

            // Parse response section
            JsonNode respNode = node.path("response");
            int statusCode = respNode.path("status_code").asInt(0);
            Map<String, List<String>> responseHeaders = parseHeaders(respNode.path("headers"));
            String responseBody = respNode.path("raw").asText("");

            return new CapturedRequest(
                    id, tunnelName, remoteAddr, method, path, fullUrl,
                    requestHeaders, requestBody,
                    statusCode, responseHeaders, responseBody,
                    durationMs, timestamp
            );
        } catch (Exception e) {
            log.debug("Failed to map ngrok request node: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, List<String>> parseHeaders(JsonNode headersNode) {
        if (headersNode == null || headersNode.isMissingNode() || !headersNode.isObject()) {
            return Map.of();
        }

        try {
            return objectMapper.convertValue(headersNode,
                    new TypeReference<Map<String, List<String>>>() {});
        } catch (IllegalArgumentException e) {
            return Map.of();
        }
    }

    private Instant parseTimestamp(String value) {
        if (value == null || value.isEmpty()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            // ngrok uses various timestamp formats; fall back gracefully
            return Instant.now();
        }
    }
}
