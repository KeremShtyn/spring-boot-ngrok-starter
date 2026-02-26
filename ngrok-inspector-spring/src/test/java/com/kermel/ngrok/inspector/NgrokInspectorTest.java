package com.kermel.ngrok.inspector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("NgrokInspector")
@ExtendWith(MockitoExtension.class)
class NgrokInspectorTest {

    @Mock
    private InspectorClient client;

    private NgrokInspector inspector;

    @BeforeEach
    void setUp() {
        inspector = new NgrokInspector(client);
    }

    @Nested
    @DisplayName("getRecentRequests")
    class GetRecentRequests {

        @Test
        void delegatesToClientWithLimit() {
            List<CapturedRequest> expected = List.of(sampleRequest("req_1", "GET", "/api/test", 200));
            when(client.listRequests(25)).thenReturn(expected);

            List<CapturedRequest> result = inspector.getRecentRequests(25);

            assertThat(result).isEqualTo(expected);
            verify(client).listRequests(25);
        }

        @Test
        void usesDefaultLimitOf50() {
            when(client.listRequests(50)).thenReturn(List.of());

            inspector.getRecentRequests();

            verify(client).listRequests(50);
        }

        @Test
        void enforcesMinimumLimit() {
            when(client.listRequests(1)).thenReturn(List.of());

            inspector.getRecentRequests(0);

            verify(client).listRequests(1);
        }
    }

    @Nested
    @DisplayName("getRequest")
    class GetRequest {

        @Test
        void delegatesToClient() {
            CapturedRequest expected = sampleRequest("req_abc", "GET", "/test", 200);
            when(client.getRequest("req_abc")).thenReturn(Optional.of(expected));

            Optional<CapturedRequest> result = inspector.getRequest("req_abc");

            assertThat(result).isPresent().contains(expected);
        }

        @Test
        void returnsEmptyWhenNotFound() {
            when(client.getRequest("missing")).thenReturn(Optional.empty());

            Optional<CapturedRequest> result = inspector.getRequest("missing");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("replay")
    class Replay {

        @Test
        void delegatesReplayWithoutTunnel() {
            CapturedResponse expected = new CapturedResponse(200, Map.of(), "OK", 50);
            when(client.replayRequest("req_1", null)).thenReturn(Optional.of(expected));

            Optional<CapturedResponse> result = inspector.replay("req_1");

            assertThat(result).isPresent().contains(expected);
            verify(client).replayRequest("req_1", null);
        }

        @Test
        void delegatesReplayWithTunnel() {
            CapturedResponse expected = new CapturedResponse(201, Map.of(), "Created", 100);
            when(client.replayRequest("req_2", "api")).thenReturn(Optional.of(expected));

            Optional<CapturedResponse> result = inspector.replay("req_2", "api");

            assertThat(result).isPresent().contains(expected);
            verify(client).replayRequest("req_2", "api");
        }
    }

    @Nested
    @DisplayName("getRequestsByPath")
    class GetRequestsByPath {

        @Test
        void filtersRequestsByPathPattern() {
            List<CapturedRequest> all = List.of(
                    sampleRequest("r1", "GET", "/api/users", 200),
                    sampleRequest("r2", "GET", "/api/orders", 200),
                    sampleRequest("r3", "GET", "/health", 200)
            );
            when(client.listRequests(50)).thenReturn(all);

            List<CapturedRequest> result = inspector.getRequestsByPath("/api/.*");

            assertThat(result).hasSize(2);
            assertThat(result).extracting(CapturedRequest::path)
                    .containsExactly("/api/users", "/api/orders");
        }

        @Test
        void returnsEmptyWhenNoMatch() {
            List<CapturedRequest> all = List.of(
                    sampleRequest("r1", "GET", "/health", 200)
            );
            when(client.listRequests(50)).thenReturn(all);

            List<CapturedRequest> result = inspector.getRequestsByPath("/api/.*");

            assertThat(result).isEmpty();
        }

        @Test
        void respectsCustomLimit() {
            when(client.listRequests(100)).thenReturn(List.of());

            inspector.getRequestsByPath("/test", 100);

            verify(client).listRequests(100);
        }
    }

    @Nested
    @DisplayName("getRequestsByMethod")
    class GetRequestsByMethod {

        @Test
        void filtersRequestsByMethod() {
            List<CapturedRequest> all = List.of(
                    sampleRequest("r1", "GET", "/api/users", 200),
                    sampleRequest("r2", "POST", "/api/orders", 201),
                    sampleRequest("r3", "GET", "/api/items", 200)
            );
            when(client.listRequests(50)).thenReturn(all);

            List<CapturedRequest> result = inspector.getRequestsByMethod("POST");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).id()).isEqualTo("r2");
        }

        @Test
        void caseInsensitiveMatch() {
            List<CapturedRequest> all = List.of(
                    sampleRequest("r1", "GET", "/test", 200)
            );
            when(client.listRequests(50)).thenReturn(all);

            List<CapturedRequest> result = inspector.getRequestsByMethod("get");

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getRequestsByStatus")
    class GetRequestsByStatus {

        @Test
        void filtersRequestsByStatusRange() {
            List<CapturedRequest> all = List.of(
                    sampleRequest("r1", "GET", "/ok", 200),
                    sampleRequest("r2", "GET", "/bad", 400),
                    sampleRequest("r3", "GET", "/error", 500),
                    sampleRequest("r4", "GET", "/notfound", 404)
            );
            when(client.listRequests(50)).thenReturn(all);

            List<CapturedRequest> result = inspector.getRequestsByStatus(400, 499);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(CapturedRequest::responseStatusCode)
                    .containsExactly(400, 404);
        }
    }

    @Nested
    @DisplayName("getErrorRequests")
    class GetErrorRequests {

        @Test
        void returnsOnly4xxAnd5xxRequests() {
            List<CapturedRequest> all = List.of(
                    sampleRequest("r1", "GET", "/ok", 200),
                    sampleRequest("r2", "GET", "/redirect", 301),
                    sampleRequest("r3", "POST", "/bad", 400),
                    sampleRequest("r4", "GET", "/error", 500)
            );
            when(client.listRequests(50)).thenReturn(all);

            List<CapturedRequest> result = inspector.getErrorRequests();

            assertThat(result).hasSize(2);
            assertThat(result).extracting(CapturedRequest::responseStatusCode)
                    .containsExactly(400, 500);
        }
    }

    @Nested
    @DisplayName("clearRequests and isAvailable")
    class UtilityMethods {

        @Test
        void clearRequestsDelegatesToClient() {
            inspector.clearRequests();
            verify(client).clearRequests();
        }

        @Test
        void isAvailableDelegatesToClient() {
            when(client.isAvailable()).thenReturn(true);
            assertThat(inspector.isAvailable()).isTrue();

            when(client.isAvailable()).thenReturn(false);
            assertThat(inspector.isAvailable()).isFalse();
        }
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
