package com.huawei.opsfactory.finops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the FinOps service.
 *
 * @since 2026-05-28
 */
@ConfigurationProperties(prefix = "finops")
public class FinOpsProperties {

    private String secretKey = "";
    private String corsOrigin = "http://127.0.0.1:5173";
    private Gateway gateway = new Gateway();
    private Scan scan = new Scan();

    /**
     * Gets the optional API secret expected by FinOps endpoints.
     *
     * @return configured secret key
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Sets the optional API secret expected by FinOps endpoints.
     *
     * @param secretKey configured secret key
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Gets the allowed browser origin.
     *
     * @return CORS origin
     */
    public String getCorsOrigin() {
        return corsOrigin;
    }

    /**
     * Sets the allowed browser origin.
     *
     * @param corsOrigin CORS origin
     */
    public void setCorsOrigin(String corsOrigin) {
        this.corsOrigin = corsOrigin;
    }

    /**
     * Gets gateway connection settings.
     *
     * @return gateway settings
     */
    public Gateway getGateway() {
        return gateway;
    }

    /**
     * Sets gateway connection settings.
     *
     * @param gateway gateway settings
     */
    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Gets snapshot refresh settings.
     *
     * @return refresh settings
     */
    public Scan getScan() {
        return scan;
    }

    /**
     * Sets snapshot refresh settings.
     *
     * @param scan refresh settings
     */
    public void setScan(Scan scan) {
        this.scan = scan;
    }

    /**
     * Snapshot refresh settings.
     */
    public static class Scan {
        private long refreshIntervalMs = 300000;
        private boolean refreshOnStartup = true;

        /**
         * Gets the refresh interval.
         *
         * @return refresh interval in milliseconds
         */
        public long getRefreshIntervalMs() {
            return refreshIntervalMs;
        }

        /**
         * Sets the refresh interval.
         *
         * @param refreshIntervalMs refresh interval in milliseconds
         */
        public void setRefreshIntervalMs(long refreshIntervalMs) {
            this.refreshIntervalMs = refreshIntervalMs;
        }

        /**
         * Gets whether startup refresh is enabled.
         *
         * @return true when refresh should run during startup
         */
        public boolean isRefreshOnStartup() {
            return refreshOnStartup;
        }

        /**
         * Sets whether startup refresh is enabled.
         *
         * @param refreshOnStartup startup refresh flag
         */
        public void setRefreshOnStartup(boolean refreshOnStartup) {
            this.refreshOnStartup = refreshOnStartup;
        }
    }

    /**
     * Gateway connection settings used to read usage snapshots.
     */
    public static class Gateway {
        private String baseUrl = "http://127.0.0.1:3000";
        private String secretKey = "";
        private String userId = "";
        private long timeoutMs = 30000;

        /**
         * Gets the gateway base URL.
         *
         * @return gateway base URL
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * Sets the gateway base URL.
         *
         * @param baseUrl gateway base URL
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * Gets the gateway API secret.
         *
         * @return gateway secret key
         */
        public String getSecretKey() {
            return secretKey;
        }

        /**
         * Sets the gateway API secret.
         *
         * @param secretKey gateway secret key
         */
        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        /**
         * Gets the gateway user identifier.
         *
         * @return gateway user identifier
         */
        public String getUserId() {
            return userId;
        }

        /**
         * Sets the gateway user identifier.
         *
         * @param userId gateway user identifier
         */
        public void setUserId(String userId) {
            this.userId = userId;
        }

        /**
         * Gets the gateway request timeout.
         *
         * @return timeout in milliseconds
         */
        public long getTimeoutMs() {
            return timeoutMs;
        }

        /**
         * Sets the gateway request timeout.
         *
         * @param timeoutMs timeout in milliseconds
         */
        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
