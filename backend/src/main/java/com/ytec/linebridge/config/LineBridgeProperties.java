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
    private AdminProperties admin = new AdminProperties();

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
}
