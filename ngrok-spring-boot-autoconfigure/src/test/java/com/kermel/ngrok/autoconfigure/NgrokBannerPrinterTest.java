package com.kermel.ngrok.autoconfigure;

import com.kermel.ngrok.core.NgrokTunnel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;

@DisplayName("NgrokBannerPrinter")
class NgrokBannerPrinterTest {

    private NgrokProperties properties;
    private NgrokBannerPrinter bannerPrinter;

    @BeforeEach
    void setUp() {
        properties = new NgrokProperties();
        bannerPrinter = new NgrokBannerPrinter(properties);
    }

    @Test
    void printSingleTunnelDoesNotThrow() {
        Collection<NgrokTunnel> tunnels = List.of(
                new NgrokTunnel("default", "https://abc123.ngrok-free.app", 8080, "http")
        );

        assertThatNoException().isThrownBy(() -> bannerPrinter.print(tunnels));
    }

    @Test
    void printMultipleTunnelsDoesNotThrow() {
        Collection<NgrokTunnel> tunnels = List.of(
                new NgrokTunnel("api", "https://api.ngrok-free.app", 8080, "http"),
                new NgrokTunnel("admin", "https://admin.ngrok-free.app", 8080, "http"),
                new NgrokTunnel("database", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp")
        );

        assertThatNoException().isThrownBy(() -> bannerPrinter.print(tunnels));
    }

    @Test
    void emptyTunnelsNoPrint() {
        assertThatNoException().isThrownBy(() -> bannerPrinter.print(Collections.emptyList()));
    }

    @Test
    void bannerDisabledNoPrint() {
        properties.getBanner().setEnabled(false);

        Collection<NgrokTunnel> tunnels = List.of(
                new NgrokTunnel("default", "https://abc123.ngrok-free.app", 8080, "http")
        );

        assertThatNoException().isThrownBy(() -> bannerPrinter.print(tunnels));
    }

    @Test
    void longTunnelNamesAndUrls() {
        Collection<NgrokTunnel> tunnels = List.of(
                new NgrokTunnel("very-long-tunnel-name-api", "https://extremely-long-subdomain-name.ngrok-free.app", 8080, "http"),
                new NgrokTunnel("db", "tcp://0.tcp.ngrok.io:12345", 5432, "tcp")
        );

        assertThatNoException().isThrownBy(() -> bannerPrinter.print(tunnels));
    }
}
