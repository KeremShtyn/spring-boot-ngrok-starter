package com.kermel.ngrok.autoconfigure;

import com.kermel.ngrok.core.NgrokStarterVersion;
import com.kermel.ngrok.core.NgrokTunnel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.Collection;

/**
 * Prints a visible banner to the console when ngrok tunnels are established.
 */
public class NgrokBannerPrinter {

    private static final Logger log = LoggerFactory.getLogger(NgrokBannerPrinter.class);

    private final NgrokProperties properties;

    public NgrokBannerPrinter(NgrokProperties properties) {
        this.properties = properties;
    }

    /**
     * Print tunnel information to the console.
     */
    public void print(Collection<NgrokTunnel> tunnels) {
        if (!properties.getBanner().isEnabled() || tunnels.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n");

        if (tunnels.size() == 1) {
            printSingleTunnel(sb, tunnels.iterator().next());
        } else {
            printMultipleTunnels(sb, tunnels);
        }

        log.info(sb.toString());

        if (properties.getBanner().isCopyToClipboard()) {
            copyToClipboard(tunnels.iterator().next().publicUrl());
        }
    }

    private void printSingleTunnel(StringBuilder sb, NgrokTunnel tunnel) {
        String url = tunnel.publicUrl();
        String forwarding = url + " -> localhost:" + tunnel.localPort();
        int contentWidth = Math.max(forwarding.length() + 6, 60);

        String border = "=".repeat(contentWidth);
        String empty = " ".repeat(contentWidth);

        sb.append("+-").append(border).append("-+\n");
        sb.append("| ").append(empty).append(" |\n");
        sb.append("| ").append(padRight("  ngrok tunnel established (v" + NgrokStarterVersion.getVersion() + ")", contentWidth)).append(" |\n");
        sb.append("| ").append(empty).append(" |\n");
        sb.append("| ").append(padRight("  Public URL:  " + url, contentWidth)).append(" |\n");
        sb.append("| ").append(padRight("  Forwarding:  " + forwarding, contentWidth)).append(" |\n");
        sb.append("| ").append(empty).append(" |\n");
        sb.append("| ").append(padRight("  Inspect:     http://localhost:" + properties.getInspection().getPort(), contentWidth)).append(" |\n");
        sb.append("| ").append(empty).append(" |\n");
        sb.append("+-").append(border).append("-+");
    }

    private void printMultipleTunnels(StringBuilder sb, Collection<NgrokTunnel> tunnels) {
        int maxNameLen = tunnels.stream()
                .mapToInt(t -> t.name().length())
                .max()
                .orElse(10);

        int maxUrlLen = tunnels.stream()
                .mapToInt(t -> t.forwardingDescription().length())
                .max()
                .orElse(40);

        int contentWidth = Math.max(maxNameLen + maxUrlLen + 10, 60);
        String border = "=".repeat(contentWidth);
        String empty = " ".repeat(contentWidth);

        sb.append("+-").append(border).append("-+\n");
        sb.append("| ").append(padRight("  ngrok tunnels established (v" + NgrokStarterVersion.getVersion() + ")", contentWidth)).append(" |\n");
        sb.append("| ").append(empty).append(" |\n");

        for (NgrokTunnel tunnel : tunnels) {
            String line = "  " + padRight(tunnel.name(), maxNameLen + 2) + tunnel.forwardingDescription();
            sb.append("| ").append(padRight(line, contentWidth)).append(" |\n");
        }

        sb.append("| ").append(empty).append(" |\n");
        sb.append("| ").append(padRight("  Inspect:  http://localhost:" + properties.getInspection().getPort(), contentWidth)).append(" |\n");
        sb.append("+-").append(border).append("-+");
    }

    private void copyToClipboard(String text) {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                log.debug("Headless environment — skipping clipboard copy");
                return;
            }
            Toolkit.getDefaultToolkit()
                    .getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            log.debug("Public URL copied to clipboard: {}", text);
        } catch (Exception e) {
            log.debug("Could not copy to clipboard: {}", e.getMessage());
        }
    }

    private static String padRight(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }
}
