package com.ytec.linebridge.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 外部呼叫端 API Client 設定 DTO
 */
public class ApiClientSaveRequest {

    @NotBlank(message = "系統代碼不可為空")
    private String clientCode;

    @NotBlank(message = "系統名稱不可為空")
    private String clientName;

    private String allowedBotCodes;
    private String allowedTemplateCodes;
    private Integer rateLimitPerMin;
    private Integer enabled;

    public String getClientCode() {
        return clientCode;
    }

    public void setClientCode(String clientCode) {
        this.clientCode = clientCode;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getAllowedBotCodes() {
        return allowedBotCodes;
    }

    public void setAllowedBotCodes(String allowedBotCodes) {
        this.allowedBotCodes = allowedBotCodes;
    }

    public String getAllowedTemplateCodes() {
        return allowedTemplateCodes;
    }

    public void setAllowedTemplateCodes(String allowedTemplateCodes) {
        this.allowedTemplateCodes = allowedTemplateCodes;
    }

    public Integer getRateLimitPerMin() {
        return rateLimitPerMin;
    }

    public void setRateLimitPerMin(Integer rateLimitPerMin) {
        this.rateLimitPerMin = rateLimitPerMin;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }
}
