package com.ytec.linebridge.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增／編輯 Bot 帳號請求 DTO（更新時若 Token/Secret 留空則保留原值）
 */
public class BotSaveRequest {

    @NotBlank(message = "Bot 代碼不可為空")
    private String botCode;

    @NotBlank(message = "Bot 名稱不可為空")
    private String name;

    private String lineBotUserId;
    private String lineBasicId;
    private String channelAccessToken;
    private String channelSecret;
    private Integer quotaLimit;
    private Integer enabled;
    private Integer welcomeEnabled;
    private String welcomeMessage;

    public String getBotCode() {
        return botCode;
    }

    public void setBotCode(String botCode) {
        this.botCode = botCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLineBotUserId() {
        return lineBotUserId;
    }

    public void setLineBotUserId(String lineBotUserId) {
        this.lineBotUserId = lineBotUserId;
    }

    public String getLineBasicId() {
        return lineBasicId;
    }

    public void setLineBasicId(String lineBasicId) {
        this.lineBasicId = lineBasicId;
    }

    public String getChannelAccessToken() {
        return channelAccessToken;
    }

    public void setChannelAccessToken(String channelAccessToken) {
        this.channelAccessToken = channelAccessToken;
    }

    public String getChannelSecret() {
        return channelSecret;
    }

    public void setChannelSecret(String channelSecret) {
        this.channelSecret = channelSecret;
    }

    public Integer getQuotaLimit() {
        return quotaLimit;
    }

    public void setQuotaLimit(Integer quotaLimit) {
        this.quotaLimit = quotaLimit;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public Integer getWelcomeEnabled() {
        return welcomeEnabled;
    }

    public void setWelcomeEnabled(Integer welcomeEnabled) {
        this.welcomeEnabled = welcomeEnabled;
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }
}
