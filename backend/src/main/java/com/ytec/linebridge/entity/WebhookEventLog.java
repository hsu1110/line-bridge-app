package com.ytec.linebridge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Webhook 接收除錯紀錄實體
 */
@TableName("LB_WEBHOOK_EVENT")
public class WebhookEventLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String webhookEventId;
    private String botCode;
    private String eventType;
    private String sourceId;
    private Integer signatureValid;
    private Integer isRedelivery;
    private String processStatus;
    private String rawPayload;
    private String errorMessage;
    private LocalDateTime createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWebhookEventId() {
        return webhookEventId;
    }

    public void setWebhookEventId(String webhookEventId) {
        this.webhookEventId = webhookEventId;
    }

    public String getBotCode() {
        return botCode;
    }

    public void setBotCode(String botCode) {
        this.botCode = botCode;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public Integer getSignatureValid() {
        return signatureValid;
    }

    public void setSignatureValid(Integer signatureValid) {
        this.signatureValid = signatureValid;
    }

    public Integer getIsRedelivery() {
        return isRedelivery;
    }

    public void setIsRedelivery(Integer isRedelivery) {
        this.isRedelivery = isRedelivery;
    }

    public String getProcessStatus() {
        return processStatus;
    }

    public void setProcessStatus(String processStatus) {
        this.processStatus = processStatus;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
