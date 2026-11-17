package com.ytec.linebridge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 對外發送訊息請求參數（兼容蛇形命名與駝峰命名）
 */
public class SendRequest {

    @JsonProperty("bot_code")
    private String botCode;

    @JsonProperty("target_code")
    private String targetCode;

    private String to;

    @JsonProperty("template_code")
    private String templateCode;

    private String message;

    private Map<String, Object> params;

    /** 是否非同步送出（預設 true：立刻回 202 + jobId；測試發送可設 false 同步回結果） */
    private Boolean async;

    public String getBotCode() {
        return botCode;
    }

    public void setBotCode(String botCode) {
        this.botCode = botCode;
    }

    public String getTargetCode() {
        return targetCode;
    }

    public void setTargetCode(String targetCode) {
        this.targetCode = targetCode;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public Boolean getAsync() {
        return async;
    }

    public void setAsync(Boolean async) {
        this.async = async;
    }
}
