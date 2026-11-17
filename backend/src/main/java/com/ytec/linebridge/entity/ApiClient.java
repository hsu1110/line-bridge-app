package com.ytec.linebridge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 外部呼叫系統 API 憑證實體
 */
@TableName("LB_API_CLIENT")
public class ApiClient {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String clientCode;
    private String clientName;
    private String apiKey;
    private String allowedBotCodes;
    private String allowedTemplateCodes;
    private Integer rateLimitPerMin;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
