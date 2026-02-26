package com.kermel.ngrok.inspector.actuator;

import com.kermel.ngrok.inspector.CapturedRequest;
import com.kermel.ngrok.inspector.NgrokInspector;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Actuator endpoint at {@code /actuator/ngrokrequests} that exposes
 * captured HTTP requests from ngrok's inspection API.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@code GET /actuator/ngrokrequests} — list recent captured requests</li>
 *   <li>{@code GET /actuator/ngrokrequests/{id}} — get a specific request</li>
 *   <li>{@code POST /actuator/ngrokrequests} — replay a captured request</li>
 * </ul>
 */
@Endpoint(id = "ngrokrequests")
public class NgrokRequestsEndpoint {

    private static final int DEFAULT_LIMIT = 50;

    private final NgrokInspector inspector;

    public NgrokRequestsEndpoint(NgrokInspector inspector) {
        this.inspector = inspector;
    }

    @ReadOperation
    public Map<String, Object> listRequests(@Nullable Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? limit : DEFAULT_LIMIT;
        List<CapturedRequest> requests = inspector.getRecentRequests(effectiveLimit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", inspector.isAvailable());
        result.put("limit", effectiveLimit);
        result.put("count", requests.size());
        result.put("requests", requests.stream()
                .map(this::requestToSummary)
                .toList());
        return result;
    }

    @ReadOperation
    public Map<String, Object> getRequest(@Selector String id) {
        Optional<CapturedRequest> request = inspector.getRequest(id);

        if (request.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", "Request not found");
            result.put("id", id);
            return result;
        }

        return requestToDetail(request.get());
    }

    @WriteOperation
    public Map<String, Object> replayRequest(String id, @Nullable String tunnelName) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", id);

        var response = inspector.replay(id, tunnelName);
        if (response.isPresent()) {
            result.put("replayed", true);
            result.put("statusCode", response.get().statusCode());
            result.put("durationMs", response.get().durationMs());
        } else {
            result.put("replayed", false);
            result.put("error", "Failed to replay request");
        }

        return result;
    }

    private Map<String, Object> requestToSummary(CapturedRequest req) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", req.id());
        map.put("method", req.method());
        map.put("path", req.path());
        map.put("responseStatusCode", req.responseStatusCode());
        map.put("durationMs", req.durationMs());
        map.put("timestamp", req.timestamp() != null ? req.timestamp().toString() : null);
        return map;
    }

    private Map<String, Object> requestToDetail(CapturedRequest req) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", req.id());
        map.put("tunnelName", req.tunnelName());
        map.put("remoteAddr", req.remoteAddr());
        map.put("method", req.method());
        map.put("path", req.path());
        map.put("fullUrl", req.fullUrl());
        map.put("requestHeaders", req.requestHeaders());
        map.put("requestBody", req.requestBody());
        map.put("responseStatusCode", req.responseStatusCode());
        map.put("responseHeaders", req.responseHeaders());
        map.put("responseBody", req.responseBody());
        map.put("durationMs", req.durationMs());
        map.put("timestamp", req.timestamp() != null ? req.timestamp().toString() : null);
        return map;
    }
}
