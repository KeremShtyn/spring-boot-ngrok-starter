package com.kermel.ngrok.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NgrokPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void defaultValues() {
        contextRunner.run(context -> {
            NgrokProperties props = context.getBean(NgrokProperties.class);

            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getAuthToken()).isNull();
            assertThat(props.getActiveProfiles()).containsExactly("dev", "local");
            assertThat(props.isProfileRestricted()).isTrue();
            assertThat(props.getRegion()).isNull();
            assertThat(props.getBinaryPath()).isNull();
            assertThat(props.getLogLevel()).isEqualTo("info");
            assertThat(props.getTunnels()).isEmpty();
            assertThat(props.getBanner().isEnabled()).isTrue();
            assertThat(props.getBanner().isCopyToClipboard()).isFalse();
            assertThat(props.getInspection().isEnabled()).isTrue();
            assertThat(props.getInspection().getPort()).isEqualTo(4040);
        });
    }

    @Test
    void defaultTunnelDefaults() {
        contextRunner.run(context -> {
            NgrokProperties props = context.getBean(NgrokProperties.class);
            NgrokProperties.TunnelProperties tunnel = props.getDefaultTunnel();

            assertThat(tunnel.getPort()).isNull();
            assertThat(tunnel.getProtocol()).isEqualTo("http");
            assertThat(tunnel.getDomain()).isNull();
            assertThat(tunnel.isHttpsOnly()).isTrue();
            assertThat(tunnel.getTrafficPolicy()).isNull();
            assertThat(tunnel.getTrafficPolicyFile()).isNull();
            assertThat(tunnel.getBasicAuth()).isNull();
            assertThat(tunnel.getAllowCidrs()).isNull();
            assertThat(tunnel.getDenyCidrs()).isNull();
            assertThat(tunnel.isFailOpen()).isTrue();
            assertThat(tunnel.getMetadata()).isNull();
        });
    }

    @Test
    void customValues() {
        contextRunner
                .withPropertyValues(
                        "ngrok.enabled=false",
                        "ngrok.auth-token=test-token",
                        "ngrok.region=eu",
                        "ngrok.log-level=debug",
                        "ngrok.profile-restricted=false",
                        "ngrok.banner.enabled=false",
                        "ngrok.banner.copy-to-clipboard=true",
                        "ngrok.inspection.enabled=false",
                        "ngrok.inspection.port=5050",
                        "ngrok.default-tunnel.port=9090",
                        "ngrok.default-tunnel.protocol=tcp",
                        "ngrok.default-tunnel.domain=my-app.ngrok.dev",
                        "ngrok.default-tunnel.https-only=false",
                        "ngrok.default-tunnel.basic-auth=user:pass",
                        "ngrok.default-tunnel.fail-open=false"
                )
                .run(context -> {
                    NgrokProperties props = context.getBean(NgrokProperties.class);

                    assertThat(props.isEnabled()).isFalse();
                    assertThat(props.getAuthToken()).isEqualTo("test-token");
                    assertThat(props.getRegion()).isEqualTo("eu");
                    assertThat(props.getLogLevel()).isEqualTo("debug");
                    assertThat(props.isProfileRestricted()).isFalse();
                    assertThat(props.getBanner().isEnabled()).isFalse();
                    assertThat(props.getBanner().isCopyToClipboard()).isTrue();
                    assertThat(props.getInspection().isEnabled()).isFalse();
                    assertThat(props.getInspection().getPort()).isEqualTo(5050);

                    NgrokProperties.TunnelProperties tunnel = props.getDefaultTunnel();
                    assertThat(tunnel.getPort()).isEqualTo(9090);
                    assertThat(tunnel.getProtocol()).isEqualTo("tcp");
                    assertThat(tunnel.getDomain()).isEqualTo("my-app.ngrok.dev");
                    assertThat(tunnel.isHttpsOnly()).isFalse();
                    assertThat(tunnel.getBasicAuth()).isEqualTo("user:pass");
                    assertThat(tunnel.isFailOpen()).isFalse();
                });
    }

    @Test
    void multiTunnelConfig() {
        contextRunner
                .withPropertyValues(
                        "ngrok.tunnels.api.port=8080",
                        "ngrok.tunnels.api.protocol=http",
                        "ngrok.tunnels.api.domain=api.test.ngrok.dev",
                        "ngrok.tunnels.database.port=5432",
                        "ngrok.tunnels.database.protocol=tcp"
                )
                .run(context -> {
                    NgrokProperties props = context.getBean(NgrokProperties.class);

                    assertThat(props.getTunnels()).hasSize(2);
                    assertThat(props.getTunnels()).containsKeys("api", "database");

                    NgrokProperties.TunnelProperties api = props.getTunnels().get("api");
                    assertThat(api.getPort()).isEqualTo(8080);
                    assertThat(api.getProtocol()).isEqualTo("http");
                    assertThat(api.getDomain()).isEqualTo("api.test.ngrok.dev");

                    NgrokProperties.TunnelProperties db = props.getTunnels().get("database");
                    assertThat(db.getPort()).isEqualTo(5432);
                    assertThat(db.getProtocol()).isEqualTo("tcp");
                });
    }

    @Configuration
    @EnableConfigurationProperties(NgrokProperties.class)
    static class TestConfig {
    }
}
