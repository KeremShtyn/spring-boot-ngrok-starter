package com.kermel.ngrok.inspector.actuator;

import com.kermel.ngrok.inspector.CapturedRequest;
import com.kermel.ngrok.inspector.CapturedResponse;
import com.kermel.ngrok.inspector.NgrokInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("NgrokRequestsEndpoint")
@ExtendWith(MockitoExtension.class)
class NgrokRequestsEndpointTest {

    @Mock
    private NgrokInspector inspector;

    private NgrokRequestsEndpoint endpoint;

    @BeforeEach
    void setUp() {
        endpoint = new NgrokRequestsEndpoint(inspector);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRequestsReturnsSummary() {
        when(inspector.isAvailable()).thenReturn(true);
        when(inspector.getRecentRequests(50)).thenReturn(List.of(
                sampleRequest("req_1", "GET", "/api/users", 200),
                sampleRequest("req_2", "POST", "/api/orders", 201)
        ));

        Map<String, Object> result = endpoint.listRequests(null);

        assertThat(result.get("available")).isEqualTo(true);
        assertThat(result.get("limit")).isEqualTo(50);
        assertThat(result.get("count")).isEqualTo(2);

        List<Map<String, Object>> requests = (List<Map<String, Object>>) result.get("requests");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).get("id")).isEqualTo("req_1");
        assertThat(requests.get(0).get("method")).isEqualTo("GET");
        assertThat(requests.get(0).get("path")).isEqualTo("/api/users");
        assertThat(requests.get(0).get("responseStatusCode")).isEqualTo(200);
    }

    @Test
    void listRequestsRespectsCustomLimit() {
        when(inspector.isAvailable()).thenReturn(true);
        when(inspector.getRecentRequests(10)).thenReturn(List.of());

        Map<String, Object> result = endpoint.listRequests(10);

        assertThat(result.get("limit")).isEqualTo(10);
        assertThat(result.get("count")).isEqualTo(0);
    }

    @Test
    void listRequestsUsesDefaultLimitForNullAndZero() {
        when(inspector.isAvailable()).thenReturn(true);
        when(inspector.getRecentRequests(50)).thenReturn(List.of());

        Map<String, Object> result = endpoint.listRequests(0);
        assertThat(result.get("limit")).isEqualTo(50);
    }

    @Test
    void getRequestReturnsDetail() {
        CapturedRequest req = sampleRequest("req_detail", "PUT", "/api/items/5", 200);
        when(inspector.getRequest("req_detail")).thenReturn(Optional.of(req));

        Map<String, Object> result = endpoint.getRequest("req_detail");

        assertThat(result.get("id")).isEqualTo("req_detail");
        assertThat(result.get("method")).isEqualTo("PUT");
        assertThat(result.get("path")).isEqualTo("/api/items/5");
        assertThat(result.get("tunnelName")).isEqualTo("default");
        assertThat(result.get("responseStatusCode")).isEqualTo(200);
    }

    @Test
    void getRequestReturnsErrorWhenNotFound() {
        when(inspector.getRequest("missing")).thenReturn(Optional.empty());

        Map<String, Object> result = endpoint.getRequest("missing");

        assertThat(result.get("error")).isEqualTo("Request not found");
        assertThat(result.get("id")).isEqualTo("missing");
    }

    @Test
    void replayRequestReturnsSuccessResult() {
        CapturedResponse response = new CapturedResponse(200, Map.of(), "OK", 45);
        when(inspector.replay("req_replay", null)).thenReturn(Optional.of(response));

        Map<String, Object> result = endpoint.replayRequest("req_replay", null);

        assertThat(result.get("replayed")).isEqualTo(true);
        assertThat(result.get("statusCode")).isEqualTo(200);
        assertThat(result.get("durationMs")).isEqualTo(45L);
    }

    @Test
    void replayRequestReturnsFailureOnError() {
        when(inspector.replay("req_fail", null)).thenReturn(Optional.empty());

        Map<String, Object> result = endpoint.replayRequest("req_fail", null);

        assertThat(result.get("replayed")).isEqualTo(false);
        assertThat(result.get("error")).isEqualTo("Failed to replay request");
    }

    @Test
    void replayRequestPassesTunnelName() {
        CapturedResponse response = new CapturedResponse(201, Map.of(), "Created", 10);
        when(inspector.replay("req_t", "api")).thenReturn(Optional.of(response));

        Map<String, Object> result = endpoint.replayRequest("req_t", "api");

        assertThat(result.get("replayed")).isEqualTo(true);
    }

    // --- Helper ---

    private CapturedRequest sampleRequest(String id, String method, String path, int statusCode) {
        return new CapturedRequest(
                id, "default", "127.0.0.1:1234",
                method, path, "https://example.ngrok.io" + path,
                Map.of(), "", statusCode, Map.of(), "",
                50, Instant.now()
        );
    }
}
