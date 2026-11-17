package com.ytec.linebridge.dto;

import com.ytec.linebridge.entity.BotAccount;

import java.time.LocalDateTime;

/**
 * 提供管理介面顯示的 Bot 視圖（Token 與 Secret 遮罩處理）
 */
public class BotAccountView {

    private String id;
    private String botCode;
    private String name;
    private String lineBotUserId;
    private String lineBasicId;
    private String channelAccessTokenMasked;
    private String channelSecretMasked;
    private boolean hasToken;
    private boolean hasSecret;
    private Integer quotaLimit;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BotAccountView from(BotAccount entity) {
        BotAccountView view = new BotAccountView();
        view.id = entity.getId();
        view.botCode = entity.getBotCode();
        view.name = entity.getName();
        view.lineBotUserId = entity.getLineBotUserId();
        view.lineBasicId = entity.getLineBasicId();
        view.hasToken = entity.getChannelAccessToken() != null && !entity.getChannelAccessToken().isBlank();
        view.hasSecret = entity.getChannelSecret() != null && !entity.getChannelSecret().isBlank();
        view.channelAccessTokenMasked = maskSecret(entity.getChannelAccessToken());
        view.channelSecretMasked = maskSecret(entity.getChannelSecret());
        view.quotaLimit = entity.getQuotaLimit();
        view.enabled = entity.getEnabled();
        view.createdAt = entity.getCreatedAt();
        view.updatedAt = entity.getUpdatedAt();
        return view;
    }

    private static String maskSecret(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "••••••••" + trimmed.substring(trimmed.length() - 4);
    }

    public String getId() {
        return id;
    }

    public String getBotCode() {
        return botCode;
    }

    public String getName() {
        return name;
    }

    public String getLineBotUserId() {
        return lineBotUserId;
    }

    public String getLineBasicId() {
        return lineBasicId;
    }

    public String getChannelAccessTokenMasked() {
        return channelAccessTokenMasked;
    }

    public String getChannelSecretMasked() {
        return channelSecretMasked;
    }

    public boolean isHasToken() {
        return hasToken;
    }

    public boolean isHasSecret() {
        return hasSecret;
    }

    public Integer getQuotaLimit() {
        return quotaLimit;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
