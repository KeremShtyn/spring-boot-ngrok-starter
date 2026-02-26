package com.kermel.ngrok.inspector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Captured Record Types")
class CapturedRecordsTest {

    @Nested
    @DisplayName("CapturedRequest")
    class CapturedRequestTests {

        @Test
        void recordAccessors() {
            Instant now = Instant.now();
            Map<String, List<String>> reqHeaders = Map.of("Accept", List.of("application/json"));
            Map<String, List<String>> respHeaders = Map.of("Content-Type", List.of("application/json"));

            CapturedRequest request = new CapturedRequest(
                    "req_123", "api-tunnel", "10.0.0.1:8080",
                    "POST", "/api/orders", "https://myapp.ngrok.io/api/orders",
                    reqHeaders, "{\"item\": \"book\"}",
                    201, respHeaders, "{\"orderId\": 42}",
                    150, now
            );

            assertThat(request.id()).isEqualTo("req_123");
            assertThat(request.tunnelName()).isEqualTo("api-tunnel");
            assertThat(request.remoteAddr()).isEqualTo("10.0.0.1:8080");
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/api/orders");
            assertThat(request.fullUrl()).isEqualTo("https://myapp.ngrok.io/api/orders");
            assertThat(request.requestHeaders()).isEqualTo(reqHeaders);
            assertThat(request.requestBody()).isEqualTo("{\"item\": \"book\"}");
            assertThat(request.responseStatusCode()).isEqualTo(201);
            assertThat(request.responseHeaders()).isEqualTo(respHeaders);
            assertThat(request.responseBody()).isEqualTo("{\"orderId\": 42}");
            assertThat(request.durationMs()).isEqualTo(150);
            assertThat(request.timestamp()).isEqualTo(now);
        }

        @Test
        void equalityBasedOnAllFields() {
            Instant now = Instant.now();
            CapturedRequest a = new CapturedRequest(
                    "req_1", "default", "1.2.3.4:5000",
                    "GET", "/test", "https://x.ngrok.io/test",
                    Map.of(), "", 200, Map.of(), "",
                    10, now
            );
            CapturedRequest b = new CapturedRequest(
                    "req_1", "default", "1.2.3.4:5000",
                    "GET", "/test", "https://x.ngrok.io/test",
                    Map.of(), "", 200, Map.of(), "",
                    10, now
            );

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void toStringContainsFields() {
            CapturedRequest request = new CapturedRequest(
                    "req_str", "default", "127.0.0.1:1234",
                    "GET", "/info", "https://x.ngrok.io/info",
                    Map.of(), "", 200, Map.of(), "",
                    5, Instant.EPOCH
            );

            String str = request.toString();
            assertThat(str).contains("req_str");
            assertThat(str).contains("GET");
            assertThat(str).contains("/info");
        }
    }

    @Nested
    @DisplayName("CapturedResponse")
    class CapturedResponseTests {

        @Test
        void recordAccessors() {
            Map<String, List<String>> headers = Map.of("Content-Type", List.of("text/plain"));

            CapturedResponse response = new CapturedResponse(
                    200, headers, "Hello World", 75
            );

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers()).isEqualTo(headers);
            assertThat(response.body()).isEqualTo("Hello World");
            assertThat(response.durationMs()).isEqualTo(75);
        }

        @Test
        void equalityBasedOnAllFields() {
            CapturedResponse a = new CapturedResponse(404, Map.of(), "Not Found", 20);
            CapturedResponse b = new CapturedResponse(404, Map.of(), "Not Found", 20);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }
    }
}
