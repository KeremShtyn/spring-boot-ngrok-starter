package com.kermel.ngrok.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for ngrok integration.
 *
 * <p>Bind these properties using the {@code ngrok} prefix in your
 * {@code application.yml} or {@code application.properties}.
 */
@ConfigurationProperties(prefix = "ngrok")
public class NgrokProperties {

    /** Master switch to enable/disable ngrok integration. */
    private boolean enabled = true;

    /** ngrok auth token. Falls back to NGROK_AUTHTOKEN env var. */
    private String authToken;

    /** Spring profiles in which ngrok should be active. */
    private List<String> activeProfiles = List.of("dev", "local");

    /**
     * Whether to activate only when specific profiles are active.
     * If false, ngrok activates regardless of profile (but still respects 'enabled').
     */
    private boolean profileRestricted = true;

    /** ngrok region (us, eu, ap, au, sa, jp, in). */
    private String region;

    /** Custom ngrok binary path (if not using auto-download). */
    private String binaryPath;

    /** Log level for ngrok process output. */
    private String logLevel = "info";

    /** Default tunnel configuration (when no multi-tunnel config is provided). */
    private TunnelProperties defaultTunnel = new TunnelProperties();

    /** Named tunnel configurations for multi-tunnel support. */
    private Map<String, TunnelProperties> tunnels = new LinkedHashMap<>();

    /** Banner configuration. */
    private BannerProperties banner = new BannerProperties();

    /** Inspection API configuration. */
    private InspectionProperties inspection = new InspectionProperties();

    /** Reconnection configuration. */
    private ReconnectionProperties reconnection = new ReconnectionProperties();

    // --- Getters and Setters ---

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public List<String> getActiveProfiles() {
        return activeProfiles;
    }

    public void setActiveProfiles(List<String> activeProfiles) {
        this.activeProfiles = activeProfiles;
    }

    public boolean isProfileRestricted() {
        return profileRestricted;
    }

    public void setProfileRestricted(boolean profileRestricted) {
        this.profileRestricted = profileRestricted;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBinaryPath() {
        return binaryPath;
    }

    public void setBinaryPath(String binaryPath) {
        this.binaryPath = binaryPath;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public TunnelProperties getDefaultTunnel() {
        return defaultTunnel;
    }

    public void setDefaultTunnel(TunnelProperties defaultTunnel) {
        this.defaultTunnel = defaultTunnel;
    }

    public Map<String, TunnelProperties> getTunnels() {
        return tunnels;
    }

    public void setTunnels(Map<String, TunnelProperties> tunnels) {
        this.tunnels = tunnels;
    }

    public BannerProperties getBanner() {
        return banner;
    }

    public void setBanner(BannerProperties banner) {
        this.banner = banner;
    }

    public InspectionProperties getInspection() {
        return inspection;
    }

    public void setInspection(InspectionProperties inspection) {
        this.inspection = inspection;
    }

    public ReconnectionProperties getReconnection() {
        return reconnection;
    }

    public void setReconnection(ReconnectionProperties reconnection) {
        this.reconnection = reconnection;
    }

    /**
     * Configuration for a single ngrok tunnel.
     */
    public static class TunnelProperties {

        /** Local port to expose. Defaults to server.port. */
        private Integer port;

        /** Protocol: http, tcp, tls. */
        private String protocol = "http";

        /** Custom ngrok domain (e.g., my-app.ngrok.dev) — requires paid plan. */
        private String domain;

        /** Bind to HTTPS only (ignore HTTP). */
        private boolean httpsOnly = true;

        /** Inline Traffic Policy YAML string. */
        private String trafficPolicy;

        /** Path to Traffic Policy YAML file. */
        private String trafficPolicyFile;

        /** Basic auth for the tunnel (username:password). */
        private String basicAuth;

        /** IP restrictions (allow list). */
        private List<String> allowCidrs;

        /** IP restrictions (deny list). */
        private List<String> denyCidrs;

        /** Circuit breaker — if ngrok fails to start, should the app still boot? */
        private boolean failOpen = true;

        /** Custom metadata for this tunnel. */
        private String metadata;

        // --- Getters and Setters ---

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public boolean isHttpsOnly() {
            return httpsOnly;
        }

        public void setHttpsOnly(boolean httpsOnly) {
            this.httpsOnly = httpsOnly;
        }

        public String getTrafficPolicy() {
            return trafficPolicy;
        }

        public void setTrafficPolicy(String trafficPolicy) {
            this.trafficPolicy = trafficPolicy;
        }

        public String getTrafficPolicyFile() {
            return trafficPolicyFile;
        }

        public void setTrafficPolicyFile(String trafficPolicyFile) {
            this.trafficPolicyFile = trafficPolicyFile;
        }

        public String getBasicAuth() {
            return basicAuth;
        }

        public void setBasicAuth(String basicAuth) {
            this.basicAuth = basicAuth;
        }

        public List<String> getAllowCidrs() {
            return allowCidrs;
        }

        public void setAllowCidrs(List<String> allowCidrs) {
            this.allowCidrs = allowCidrs;
        }

        public List<String> getDenyCidrs() {
            return denyCidrs;
        }

        public void setDenyCidrs(List<String> denyCidrs) {
            this.denyCidrs = denyCidrs;
        }

        public boolean isFailOpen() {
            return failOpen;
        }

        public void setFailOpen(boolean failOpen) {
            this.failOpen = failOpen;
        }

        public String getMetadata() {
            return metadata;
        }

        public void setMetadata(String metadata) {
            this.metadata = metadata;
        }
    }

    /**
     * Banner display configuration.
     */
    public static class BannerProperties {

        /** Show ngrok public URL in startup banner. */
        private boolean enabled = true;

        /** Copy public URL to system clipboard on startup. */
        private boolean copyToClipboard = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isCopyToClipboard() {
            return copyToClipboard;
        }

        public void setCopyToClipboard(boolean copyToClipboard) {
            this.copyToClipboard = copyToClipboard;
        }
    }

    /**
     * Inspection API configuration.
     */
    public static class InspectionProperties {

        /** Enable the ngrok inspection API integration. */
        private boolean enabled = true;

        /** ngrok inspection API port. */
        private int port = 4040;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    /**
     * Reconnection configuration for automatic tunnel recovery.
     */
    public static class ReconnectionProperties {

        /** Enable automatic reconnection when tunnels drop. */
        private boolean enabled = true;

        /** Interval in seconds between health checks for tunnel liveness. */
        private int checkIntervalSeconds = 30;

        /** Maximum number of reconnection attempts before giving up. 0 = unlimited. */
        private int maxAttempts = 5;

        /** Initial delay in seconds before the first reconnection attempt. */
        private int initialDelaySeconds = 2;

        /** Multiplier applied to delay between successive reconnection attempts. */
        private double backoffMultiplier = 2.0;

        /** Maximum delay in seconds between reconnection attempts. */
        private int maxDelaySeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCheckIntervalSeconds() {
            return checkIntervalSeconds;
        }

        public void setCheckIntervalSeconds(int checkIntervalSeconds) {
            this.checkIntervalSeconds = checkIntervalSeconds;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public int getInitialDelaySeconds() {
            return initialDelaySeconds;
        }

        public void setInitialDelaySeconds(int initialDelaySeconds) {
            this.initialDelaySeconds = initialDelaySeconds;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }

        public int getMaxDelaySeconds() {
            return maxDelaySeconds;
        }

        public void setMaxDelaySeconds(int maxDelaySeconds) {
            this.maxDelaySeconds = maxDelaySeconds;
        }
    }

    /**
     * Retry configuration for initial tunnel creation.
     */
    public static class RetryProperties {

        /** Maximum number of retry attempts for tunnel creation. */
        private int maxAttempts = 3;

        /** Delay in milliseconds between retry attempts. */
        private long delayMs = 1000;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }
    }
}
