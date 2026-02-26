package com.kermel.ngrok.policy.dsl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyAction builders")
class PolicyActionTest {

    @Nested
    @DisplayName("DenyAction")
    class DenyTests {

        @Test
        void denyWithStatusCode() {
            DenyAction action = PolicyAction.deny(403);
            assertThat(action.getType()).isEqualTo("deny");
            assertThat(action.getConfig()).containsEntry("status_code", 403);
            assertThat(action.getConfig()).doesNotContainKey("content");
        }

        @Test
        void denyWithMessage() {
            DenyAction action = PolicyAction.deny(429, "Too many requests");
            assertThat(action.getConfig())
                    .containsEntry("status_code", 429)
                    .containsEntry("content", "Too many requests");
        }
    }

    @Nested
    @DisplayName("RateLimitAction")
    class RateLimitTests {

        @Test
        void rateLimitDefaults() {
            RateLimitAction action = PolicyAction.rateLimit().build();
            assertThat(action.getType()).isEqualTo("rate-limit");
            assertThat(action.getConfig())
                    .containsEntry("algorithm", "sliding_window")
                    .containsEntry("capacity", 100)
                    .containsEntry("rate", "60s");
            assertThat(action.getConfig()).doesNotContainKey("bucket_key");
        }

        @Test
        void rateLimitCustom() {
            RateLimitAction action = PolicyAction.rateLimit()
                    .algorithm("fixed_window")
                    .capacity(50)
                    .rate("30s")
                    .bucketKey("conn.client_ip")
                    .build();
            assertThat(action.getConfig())
                    .containsEntry("algorithm", "fixed_window")
                    .containsEntry("capacity", 50)
                    .containsEntry("rate", "30s");
            @SuppressWarnings("unchecked")
            List<String> bucketKeys = (List<String>) action.getConfig().get("bucket_key");
            assertThat(bucketKeys).containsExactly("conn.client_ip");
        }

        @Test
        void rateLimitMultipleBucketKeys() {
            RateLimitAction action = PolicyAction.rateLimit()
                    .bucketKey("conn.client_ip", "req.headers['x-api-key']")
                    .build();
            @SuppressWarnings("unchecked")
            List<String> bucketKeys = (List<String>) action.getConfig().get("bucket_key");
            assertThat(bucketKeys).containsExactly("conn.client_ip", "req.headers['x-api-key']");
        }
    }

    @Nested
    @DisplayName("JwtAction")
    class JwtTests {

        @Test
        void jwtMinimal() {
            JwtAction action = PolicyAction.jwt()
                    .issuer("https://auth.example.com")
                    .build();
            assertThat(action.getType()).isEqualTo("jwt-validation");
            @SuppressWarnings("unchecked")
            Map<String, Object> issuer = (Map<String, Object>) action.getConfig().get("issuer");
            assertThat(issuer).containsEntry("value", "https://auth.example.com");
        }

        @Test
        void jwtFull() {
            JwtAction action = PolicyAction.jwt()
                    .issuer("https://auth.example.com")
                    .audience("my-api")
                    .jwksUrl("https://auth.example.com/.well-known/jwks.json")
                    .allowedAlgorithm("RS256")
                    .tokenLocation("header")
                    .build();
            assertThat(action.getConfig()).containsKey("issuer");
            assertThat(action.getConfig()).containsKey("token");
            @SuppressWarnings("unchecked")
            Map<String, Object> token = (Map<String, Object>) action.getConfig().get("token");
            assertThat(token).containsEntry("location", "header");
        }
    }

    @Nested
    @DisplayName("CustomResponseAction")
    class CustomResponseTests {

        @Test
        void customResponseDefaults() {
            CustomResponseAction action = PolicyAction.customResponse().build();
            assertThat(action.getType()).isEqualTo("custom-response");
            assertThat(action.getConfig()).containsEntry("status_code", 200);
        }

        @Test
        void customResponseFull() {
            CustomResponseAction action = PolicyAction.customResponse()
                    .statusCode(503)
                    .content("Service temporarily unavailable")
                    .header("Retry-After", "30")
                    .build();
            assertThat(action.getConfig())
                    .containsEntry("status_code", 503)
                    .containsEntry("content", "Service temporarily unavailable");
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) action.getConfig().get("headers");
            assertThat(headers).containsEntry("Retry-After", "30");
        }
    }

    @Nested
    @DisplayName("RedirectAction")
    class RedirectTests {

        @Test
        void redirectDefaultStatus() {
            RedirectAction action = PolicyAction.redirect("https://example.com");
            assertThat(action.getType()).isEqualTo("redirect");
            assertThat(action.getConfig())
                    .containsEntry("to", "https://example.com")
                    .containsEntry("status_code", 301);
        }

        @Test
        void redirectTemporary() {
            RedirectAction action = PolicyAction.redirect("https://example.com/new", 302);
            assertThat(action.getConfig())
                    .containsEntry("to", "https://example.com/new")
                    .containsEntry("status_code", 302);
        }
    }

    @Nested
    @DisplayName("AddHeadersAction")
    class AddHeadersTests {

        @Test
        void addSingleHeader() {
            AddHeadersAction action = PolicyAction.addHeaders()
                    .header("X-Frame-Options", "DENY")
                    .build();
            assertThat(action.getType()).isEqualTo("add-headers");
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) action.getConfig().get("headers");
            assertThat(headers).containsEntry("X-Frame-Options", "DENY");
        }

        @Test
        void addMultipleHeaders() {
            AddHeadersAction action = PolicyAction.addHeaders()
                    .header("X-Frame-Options", "DENY")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("X-XSS-Protection", "1; mode=block")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) action.getConfig().get("headers");
            assertThat(headers).hasSize(3);
        }
    }

    @Nested
    @DisplayName("RemoveHeadersAction")
    class RemoveHeadersTests {

        @Test
        void removeSingleHeader() {
            RemoveHeadersAction action = PolicyAction.removeHeaders()
                    .header("X-Powered-By")
                    .build();
            assertThat(action.getType()).isEqualTo("remove-headers");
            @SuppressWarnings("unchecked")
            List<String> headers = (List<String>) action.getConfig().get("headers");
            assertThat(headers).containsExactly("X-Powered-By");
        }

        @Test
        void removeMultipleHeaders() {
            RemoveHeadersAction action = PolicyAction.removeHeaders()
                    .header("X-Powered-By")
                    .header("Server")
                    .build();
            @SuppressWarnings("unchecked")
            List<String> headers = (List<String>) action.getConfig().get("headers");
            assertThat(headers).containsExactly("X-Powered-By", "Server");
        }
    }

    @Nested
    @DisplayName("UrlRewriteAction")
    class UrlRewriteTests {

        @Test
        void urlRewrite() {
            UrlRewriteAction action = PolicyAction.urlRewrite()
                    .from("/api/v1/(.*)")
                    .to("/v1/$1")
                    .build();
            assertThat(action.getType()).isEqualTo("url-rewrite");
            assertThat(action.getConfig())
                    .containsEntry("from", "/api/v1/(.*)")
                    .containsEntry("to", "/v1/$1");
        }
    }

    @Nested
    @DisplayName("CompressResponseAction")
    class CompressTests {

        @Test
        void compressNoAlgorithms() {
            CompressResponseAction action = PolicyAction.compressResponse().build();
            assertThat(action.getType()).isEqualTo("compress-response");
            assertThat(action.getConfig()).doesNotContainKey("algorithms");
        }

        @Test
        void compressWithAlgorithms() {
            CompressResponseAction action = PolicyAction.compressResponse()
                    .algorithms("gzip", "br", "deflate")
                    .build();
            @SuppressWarnings("unchecked")
            List<String> algorithms = (List<String>) action.getConfig().get("algorithms");
            assertThat(algorithms).containsExactly("gzip", "br", "deflate");
        }
    }

    @Nested
    @DisplayName("LogAction")
    class LogTests {

        @Test
        void logNoMetadata() {
            LogAction action = PolicyAction.log().build();
            assertThat(action.getType()).isEqualTo("log");
            assertThat(action.getConfig()).doesNotContainKey("metadata");
        }

        @Test
        void logWithMetadata() {
            LogAction action = PolicyAction.log()
                    .metadata("env", "dev")
                    .metadata("app", "test")
                    .build();
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) action.getConfig().get("metadata");
            assertThat(metadata)
                    .containsEntry("env", "dev")
                    .containsEntry("app", "test");
        }
    }
}
