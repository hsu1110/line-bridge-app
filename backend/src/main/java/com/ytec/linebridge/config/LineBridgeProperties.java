package com.ytec.linebridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 系統自訂參數配置
 */
@ConfigurationProperties(prefix = "line-bridge")
public class LineBridgeProperties {

    private String lineApiBase = "https://api.line.me/v2/bot";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 8000;
    private boolean strictBindingCheck = true;
    private boolean allowAnonymousSend = false;
    private int dashboardQuotaCacheSeconds = 60;
    private AdminProperties admin = new AdminProperties();
    private WebhookProperties webhook = new WebhookProperties();

    public String getLineApiBase() {
        return lineApiBase;
    }

    public void setLineApiBase(String lineApiBase) {
        this.lineApiBase = lineApiBase;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public boolean isStrictBindingCheck() {
        return strictBindingCheck;
    }

    public void setStrictBindingCheck(boolean strictBindingCheck) {
        this.strictBindingCheck = strictBindingCheck;
    }

    public boolean isAllowAnonymousSend() {
        return allowAnonymousSend;
    }

    public void setAllowAnonymousSend(boolean allowAnonymousSend) {
        this.allowAnonymousSend = allowAnonymousSend;
    }

    public AdminProperties getAdmin() {
        return admin;
    }

    public void setAdmin(AdminProperties admin) {
        this.admin = admin;
    }

    public int getDashboardQuotaCacheSeconds() {
        return dashboardQuotaCacheSeconds;
    }

    public void setDashboardQuotaCacheSeconds(int dashboardQuotaCacheSeconds) {
        this.dashboardQuotaCacheSeconds = dashboardQuotaCacheSeconds;
    }

    public WebhookProperties getWebhook() {
        return webhook;
    }

    public void setWebhook(WebhookProperties webhook) {
        this.webhook = webhook;
    }

    public static class AdminProperties {
        private String username = "admin";
        private String password = "change_me";
        private String jwtSecret = "YtecLineBridgeChangeThisJwtSecretKeyTo32CharsPlus!";
        private int jwtExpireHours = 24;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getJwtSecret() {
            return jwtSecret;
        }

        public void setJwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
        }

        public int getJwtExpireHours() {
            return jwtExpireHours;
        }

        public void setJwtExpireHours(int jwtExpireHours) {
            this.jwtExpireHours = jwtExpireHours;
        }
    }

    public static class WebhookProperties {
        private int maxBodyBytes = 1048576;
        private int failedRateLimitPerMin = 20;
        private int failedPayloadLogMaxChars = 2000;

        public int getMaxBodyBytes() {
            return maxBodyBytes;
        }

        public void setMaxBodyBytes(int maxBodyBytes) {
            this.maxBodyBytes = maxBodyBytes;
        }

        public int getFailedRateLimitPerMin() {
            return failedRateLimitPerMin;
        }

        public void setFailedRateLimitPerMin(int failedRateLimitPerMin) {
            this.failedRateLimitPerMin = failedRateLimitPerMin;
        }

        public int getFailedPayloadLogMaxChars() {
            return failedPayloadLogMaxChars;
        }

        public void setFailedPayloadLogMaxChars(int failedPayloadLogMaxChars) {
            this.failedPayloadLogMaxChars = failedPayloadLogMaxChars;
        }
    }
}
