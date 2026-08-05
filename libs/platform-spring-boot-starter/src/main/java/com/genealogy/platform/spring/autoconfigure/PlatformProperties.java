package com.genealogy.platform.spring.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties exposed by every service that depends on
 * {@code platform-spring-boot-starter}.
 *
 * <p>Bound from the {@code platform.*} prefix (see
 * {@code application/platform-spring-boot-defaults.yml} for the
 * default values). All defaults are safe-by-default: tenant context
 * is required for every request, JWT validation is strict and the
 * audit hook is always on.
 */
@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {

    private final Tenant tenant = new Tenant();
    private final Security security = new Security();
    private final Audit audit = new Audit();
    private final Openfeature openfeature = new Openfeature();
    private final Shutdown shutdown = new Shutdown();
    private final Otel otel = new Otel();

    public Tenant getTenant() {
        return tenant;
    }

    public Security getSecurity() {
        return security;
    }

    public Audit getAudit() {
        return audit;
    }

    public Openfeature getOpenfeature() {
        return openfeature;
    }

    public Shutdown getShutdown() {
        return shutdown;
    }

    public Otel getOtel() {
        return otel;
    }

    public static class Tenant {
        /** Whether the {@code X-Tenant-Id} header is required on every request. */
        private boolean headerRequired = true;
        /** Maximum accepted length of the opaque tenant id. */
        private int maxIdLength = 64;

        public boolean isHeaderRequired() {
            return headerRequired;
        }

        public void setHeaderRequired(boolean headerRequired) {
            this.headerRequired = headerRequired;
        }

        public int getMaxIdLength() {
            return maxIdLength;
        }

        public void setMaxIdLength(int maxIdLength) {
            this.maxIdLength = maxIdLength;
        }
    }

    public static class Security {
        /** Required Keycloak issuer URL (used to fetch JWKS at startup). */
        private String issuerUri;
        /** Expected JWT audience. */
        private String audience;
        /** Whether the service must reject any request that does not carry a valid token. */
        private boolean requireBearerToken = true;

        public String getIssuerUri() {
            return issuerUri;
        }

        public void setIssuerUri(String issuerUri) {
            this.issuerUri = issuerUri;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public boolean isRequireBearerToken() {
            return requireBearerToken;
        }

        public void setRequireBearerToken(boolean requireBearerToken) {
            this.requireBearerToken = requireBearerToken;
        }
    }

    public static class Audit {
        /** Whether every authenticated mutation must emit an audit event. */
        private boolean enabled = true;
        /** Micrometer metric name for the audit counter. */
        private String metricName = "platform.audit.events";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMetricName() {
            return metricName;
        }

        public void setMetricName(String metricName) {
            this.metricName = metricName;
        }
    }

    public static class Openfeature {
        /** Provider name to register. Defaults to {@code noop} (safe fallback). */
        private String provider = "noop";
        /** Flagsmith base URL, used only when {@link #provider} = {@code flagsmith}. */
        private String flagsmithBaseUrl;
        /** Optional API token for Flagsmith. Prefer a runtime secret. */
        private String flagsmithApiToken;
        /** Cache TTL for flag evaluation (milliseconds). */
        private long cacheTtlMillis = 30_000L;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getFlagsmithBaseUrl() {
            return flagsmithBaseUrl;
        }

        public void setFlagsmithBaseUrl(String flagsmithBaseUrl) {
            this.flagsmithBaseUrl = flagsmithBaseUrl;
        }

        public String getFlagsmithApiToken() {
            return flagsmithApiToken;
        }

        public void setFlagsmithApiToken(String flagsmithApiToken) {
            this.flagsmithApiToken = flagsmithApiToken;
        }

        public long getCacheTtlMillis() {
            return cacheTtlMillis;
        }

        public void setCacheTtlMillis(long cacheTtlMillis) {
            this.cacheTtlMillis = cacheTtlMillis;
        }
    }

    public static class Shutdown {
        /** Graceful shutdown timeout for in-flight HTTP requests. */
        private long timeoutSeconds = 30L;

        public long getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(long timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Otel {
        /** OpenTelemetry service name. Defaults to {@code spring.application.name}. */
        private String serviceName;
        /** OTLP exporter endpoint. */
        private String exporterEndpoint;
        /** Whether to sample 100% of traces. Defaults to false (parent-based). */
        private boolean alwaysSample = false;

        public String getServiceName() {
            return serviceName;
        }

        public void setServiceName(String serviceName) {
            this.serviceName = serviceName;
        }

        public String getExporterEndpoint() {
            return exporterEndpoint;
        }

        public void setExporterEndpoint(String exporterEndpoint) {
            this.exporterEndpoint = exporterEndpoint;
        }

        public boolean isAlwaysSample() {
            return alwaysSample;
        }

        public void setAlwaysSample(boolean alwaysSample) {
            this.alwaysSample = alwaysSample;
        }
    }
}
