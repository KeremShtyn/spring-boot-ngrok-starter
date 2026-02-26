package com.kermel.ngrok.inspector;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("InspectorClient")
class InspectorClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("listRequests")
    class ListRequests {

        @Test
        void parsesMultipleRequests() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("http://localhost:4040/api/requests/http?limit=10"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                            {
                              "requests": [
                                {
                                  "id": "req_001",
                                  "tunnel_name": "default",
                                  "remote_addr": "1.2.3.4:5000",
                                  "start": "2025-01-15T10:30:00Z",
                                  "duration": 45000000,
                                  "request": {
                                    "method": "GET",
                                    "uri": "/api/users",
                                    "headers": {"Host": ["example.ngrok.io"], "Accept": ["application/json"]},
                                    "raw": ""
                                  },
                                  "response": {
                                    "status": "200 OK",
                                    "status_code": 200,
                                    "headers": {"Content-Type": ["application/json"]},
                                    "raw": "[{\\"id\\": 1}]"
                                  }
                                },
                                {
                                  "id": "req_002",
                                  "tunnel_name": "default",
                                  "remote_addr": "5.6.7.8:6000",
                                  "start": "2025-01-15T10:31:00Z",
                                  "duration": 120000000,
                                  "request": {
                                    "method": "POST",
                                    "uri": "/api/orders",
                                    "headers": {"Host": ["example.ngrok.io"]},
                                    "raw": "{\\"item\\": \\"book\\"}"
                                  },
                                  "response": {
                                    "status": "201 Created",
                                    "status_code": 201,
                                    "headers": {},
                                    "raw": ""
                                  }
                                }
                              ]
                            }
                            """, MediaType.APPLICATION_JSON));

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            List<CapturedRequest> requests = client.listRequests(10);

            assertThat(requests).hasSize(2);

            CapturedRequest first = requests.get(0);
            assertThat(first.id()).isEqualTo("req_001");
            assertThat(first.method()).isEqualTo("GET");
            assertThat(first.path()).isEqualTo("/api/users");
            assertThat(first.fullUrl()).isEqualTo("https://example.ngrok.io/api/users");
            assertThat(first.responseStatusCode()).isEqualTo(200);
            assertThat(first.durationMs()).isEqualTo(45);
            assertThat(first.requestHeaders()).containsKey("Accept");

            CapturedRequest second = requests.get(1);
            assertThat(second.id()).isEqualTo("req_002");
            assertThat(second.method()).isEqualTo("POST");
            assertThat(second.responseStatusCode()).isEqualTo(201);
            assertThat(second.durationMs()).isEqualTo(120);

            server.verify();
        }

        @Test
        void returnsEmptyListOnApiError() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("http://localhost:4040/api/requests/http?limit=50"))
                    .andRespond(withServerError());

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            List<CapturedRequest> requests = client.listRequests(50);

            assertThat(requests).isEmpty();
            server.verify();
        }

        @Test
        void returnsEmptyListOnEmptyResponse() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("http://localhost:4040/api/requests/http?limit=10"))
                    .andRespond(withSuccess("{\"requests\": []}", MediaType.APPLICATION_JSON));

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            List<CapturedRequest> requests = client.listRequests(10);

            assertThat(requests).isEmpty();
            server.verify();
        }

        @Test
        void handlesNullBody() {
            InspectorClient client = new InspectorClient(RestClient.create(), objectMapper);
            List<CapturedRequest> result = client.parseRequestList(null);
            assertThat(result).isEmpty();
        }

        @Test
        void handlesMalformedJson() {
            InspectorClient client = new InspectorClient(RestClient.create(), objectMapper);
            List<CapturedRequest> result = client.parseRequestList("{not valid json");
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getRequest")
    class GetRequest {

        @Test
        void fetchesSingleRequest() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("http://localhost:4040/api/requests/http/req_abc"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("""
                            {
                              "id": "req_abc",
                              "tunnel_name": "api",
                              "remote_addr": "10.0.0.1:8080",
                              "start": "2025-02-01T12:00:00Z",
                              "duration": 5000000,
                              "request": {
                                "method": "DELETE",
                                "uri": "/api/items/42",
                                "headers": {"Host": ["myapp.ngrok.io"]},
                                "raw": ""
                              },
                              "response": {
                                "status": "204 No Content",
                                "status_code": 204,
                                "headers": {},
                                "raw": ""
                              }
                            }
                            """, MediaType.APPLICATION_JSON));

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            Optional<CapturedRequest> result = client.getRequest("req_abc");

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo("req_abc");
            assertThat(result.get().tunnelName()).isEqualTo("api");
            assertThat(result.get().method()).isEqualTo("DELETE");
            assertThat(result.get().path()).isEqualTo("/api/items/42");
            assertThat(result.get().responseStatusCode()).isEqualTo(204);
            assertThat(result.get().durationMs()).isEqualTo(5);

            server.verify();
        }

        @Test
        void returnsEmptyOnNotFound() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("http://localhost:4040/api/requests/http/nonexistent"))
                    .andRespond(withResourceNotFound());

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            Optional<CapturedRequest> result = client.getRequest("nonexistent");

            assertThat(result).isEmpty();
            server.verify();
        }
    }

    @Nested
    @DisplayName("replayRequest")
    class ReplayRequest {

        @Test
        void sendsReplayPostRequest() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            // Replay POST
            server.expect(requestTo("http://localhost:4040/api/requests/http"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess());

            // Subsequent GET to fetch the replayed result
            server.expect(requestTo("http://localhost:4040/api/requests/http?limit=1"))
                    .andRespond(withSuccess("""
                            {
                              "requests": [{
                                "id": "req_replay_001",
                                "tunnel_name": "default",
                                "remote_addr": "127.0.0.1:4040",
                                "start": "2025-02-01T12:05:00Z",
                                "duration": 30000000,
                                "request": {
                                  "method": "GET",
                                  "uri": "/api/test",
                                  "headers": {},
                                  "raw": ""
                                },
                                "response": {
                                  "status": "200 OK",
                                  "status_code": 200,
                                  "headers": {"Content-Type": ["text/plain"]},
                                  "raw": "OK"
                                }
                              }]
                            }
                            """, MediaType.APPLICATION_JSON));

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            Optional<CapturedResponse> result = client.replayRequest("req_001", "default");

            assertThat(result).isPresent();
            assertThat(result.get().statusCode()).isEqualTo(200);
            assertThat(result.get().durationMs()).isEqualTo(30);

            server.verify();
        }

        @Test
        void returnsEmptyOnReplayFailure() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("http://localhost:4040/api/requests/http"))
                    .andRespond(withServerError());

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            Optional<CapturedResponse> result = client.replayRequest("req_fail", null);

            assertThat(result).isEmpty();
            server.verify();
        }
    }

    @Nested
    @DisplayName("clearRequests")
    class ClearRequests {

        @Test
        void sendsDeleteRequest() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("http://localhost:4040/api/requests/http"))
                    .andExpect(method(HttpMethod.DELETE))
                    .andRespond(withSuccess());

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            client.clearRequests();

            server.verify();
        }

        @Test
        void handlesErrorGracefully() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("http://localhost:4040/api/requests/http"))
                    .andRespond(withServerError());

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            // Should not throw
            client.clearRequests();

            server.verify();
        }
    }

    @Nested
    @DisplayName("isAvailable")
    class IsAvailable {

        @Test
        void returnsTrueWhenApiResponds() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("http://localhost:4040/api/requests/http?limit=0"))
                    .andRespond(withSuccess());

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            assertThat(client.isAvailable()).isTrue();

            server.verify();
        }

        @Test
        void returnsFalseWhenApiUnavailable() {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:4040");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

            server.expect(requestTo("http://localhost:4040/api/requests/http?limit=0"))
                    .andRespond(withServerError());

            InspectorClient client = new InspectorClient(builder.build(), objectMapper);
            assertThat(client.isAvailable()).isFalse();

            server.verify();
        }
    }

    @Nested
    @DisplayName("JSON parsing edge cases")
    class JsonParsing {

        @Test
        void handlesMissingRequestSection() {
            InspectorClient client = new InspectorClient(RestClient.create(), objectMapper);
            CapturedRequest result = client.parseSingleRequest("""
                    {
                      "id": "req_minimal",
                      "tunnel_name": "default",
                      "duration": 0
                    }
                    """);

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo("req_minimal");
            assertThat(result.method()).isEmpty();
            assertThat(result.path()).isEmpty();
        }

        @Test
        void handlesMissingResponseSection() {
            InspectorClient client = new InspectorClient(RestClient.create(), objectMapper);
            CapturedRequest result = client.parseSingleRequest("""
                    {
                      "id": "req_noresp",
                      "duration": 1000000,
                      "request": {
                        "method": "GET",
                        "uri": "/test",
                        "headers": {},
                        "raw": ""
                      }
                    }
                    """);

            assertThat(result).isNotNull();
            assertThat(result.responseStatusCode()).isZero();
            assertThat(result.responseHeaders()).isEmpty();
        }

        @Test
        void parsesNullJsonReturnsNull() {
            InspectorClient client = new InspectorClient(RestClient.create(), objectMapper);
            assertThat(client.parseSingleRequest(null)).isNull();
        }

        @Test
        void parsesBlankJsonReturnsNull() {
            InspectorClient client = new InspectorClient(RestClient.create(), objectMapper);
            assertThat(client.parseSingleRequest("   ")).isNull();
        }

        @Test
        void durationConvertedFromNanosToMillis() {
            InspectorClient client = new InspectorClient(RestClient.create(), objectMapper);
            CapturedRequest result = client.parseSingleRequest("""
                    {
                      "id": "req_dur",
                      "duration": 250000000,
                      "request": {"method": "GET", "uri": "/", "headers": {}, "raw": ""},
                      "response": {"status_code": 200, "headers": {}, "raw": ""}
                    }
                    """);

            assertThat(result).isNotNull();
            assertThat(result.durationMs()).isEqualTo(250);
        }

        @Test
        void fullUrlBuiltFromHostHeader() {
            InspectorClient client = new InspectorClient(RestClient.create(), objectMapper);
            CapturedRequest result = client.parseSingleRequest("""
                    {
                      "id": "req_host",
                      "duration": 0,
                      "request": {
                        "method": "GET",
                        "uri": "/api/test",
                        "headers": {"Host": ["myapp.ngrok.dev"]},
                        "raw": ""
                      },
                      "response": {"status_code": 200, "headers": {}, "raw": ""}
                    }
                    """);

            assertThat(result).isNotNull();
            assertThat(result.fullUrl()).isEqualTo("https://myapp.ngrok.dev/api/test");
        }

        @Test
        void fullUrlFallsBackToPathWhenNoHost() {
            InspectorClient client = new InspectorClient(RestClient.create(), objectMapper);
            CapturedRequest result = client.parseSingleRequest("""
                    {
                      "id": "req_nohost",
                      "duration": 0,
                      "request": {
                        "method": "GET",
                        "uri": "/fallback",
                        "headers": {},
                        "raw": ""
                      },
                      "response": {"status_code": 200, "headers": {}, "raw": ""}
                    }
                    """);

            assertThat(result).isNotNull();
            assertThat(result.fullUrl()).isEqualTo("/fallback");
        }
    }
}
