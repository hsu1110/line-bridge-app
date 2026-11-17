package com.ytec.linebridge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytec.linebridge.entity.BotAccount;
import com.ytec.linebridge.entity.WebhookEventLog;
import com.ytec.linebridge.exception.BusinessException;
import com.ytec.linebridge.mapper.BotAccountMapper;
import com.ytec.linebridge.mapper.WebhookEventLogMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LINE Webhook 接收服務（負責 Raw Body 驗簽、去重與寫入除錯記錄）
 */
@Service
public class WebhookService {

    private final BotAccountMapper botAccountMapper;
    private final WebhookEventLogMapper webhookEventLogMapper;
    private final AsyncWebhookExecutor asyncWebhookExecutor;
    private final ObjectMapper objectMapper;

    public WebhookService(
            BotAccountMapper botAccountMapper,
            WebhookEventLogMapper webhookEventLogMapper,
            AsyncWebhookExecutor asyncWebhookExecutor,
            ObjectMapper objectMapper) {
        this.botAccountMapper = botAccountMapper;
        this.webhookEventLogMapper = webhookEventLogMapper;
        this.asyncWebhookExecutor = asyncWebhookExecutor;
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public void handleWebhook(String botCodeParam, byte[] rawBody, String signature) {
        String rawPayload = new String(rawBody, StandardCharsets.UTF_8);
        Map<String, Object> payloadMap;
        try {
            payloadMap = objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception e) {
            saveSimpleLog(null, botCodeParam, "invalid_json", null, false, false, "FAILED", rawPayload, "JSON 解析失敗: " + e.getMessage());
            throw new BusinessException(HttpStatus.BAD_REQUEST, "無效的 Webhook JSON 格式");
        }

        String destination = payloadMap.get("destination") != null ? payloadMap.get("destination").toString() : null;
        BotAccount bot = resolveBot(botCodeParam, destination);

        if (bot == null) {
            saveSimpleLog(null, botCodeParam != null ? botCodeParam : destination, "unknown_bot", destination, false, false, "FAILED", rawPayload, "找不到對應的機器人設定");
            throw new BusinessException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        if (bot.getEnabled() == null || bot.getEnabled() != 1) {
            saveSimpleLog(null, bot.getBotCode(), "bot_disabled", destination, false, false, "FAILED", rawPayload, "機器人已停用");
            throw new BusinessException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        // 若尚未綁定 LINE Bot User ID，自動回填 destination 便於日後比對
        if ((bot.getLineBotUserId() == null || bot.getLineBotUserId().isBlank()) && destination != null && !destination.isBlank()) {
            BotAccount patch = new BotAccount();
            patch.setId(bot.getId());
            patch.setLineBotUserId(destination);
            patch.setUpdatedAt(LocalDateTime.now());
            botAccountMapper.updateById(patch);
        }

        if (!verifySignature(rawBody, bot.getChannelSecret(), signature)) {
            saveSimpleLog(null, bot.getBotCode(), "invalid_signature", destination, false, false, "FAILED", rawPayload, "X-Line-Signature 驗證失敗");
            throw new BusinessException(HttpStatus.FORBIDDEN, "Invalid Signature");
        }

        List<Map<String, Object>> events = (List<Map<String, Object>>) payloadMap.get("events");
        if (events == null || events.isEmpty()) {
            // LINE Console Verify 按鈕會送空 events 陣列，直接紀錄並秒回 200 OK
            saveSimpleLog(null, bot.getBotCode(), "verify", destination, true, false, "PROCESSED", rawPayload, null);
            return;
        }

        for (Map<String, Object> event : events) {
            String eventId = event.get("webhookEventId") != null ? event.get("webhookEventId").toString() : null;
            String eventType = event.get("type") != null ? event.get("type").toString() : "unknown";
            String sourceId = extractSourceId(event);
            boolean isRedelivery = extractRedelivery(event);

            if (eventId != null && isDuplicateEvent(eventId)) {
                saveSimpleLog(null, bot.getBotCode(), eventType, sourceId, true, isRedelivery, "DUPLICATE", rawPayload, "重複投遞事件已忽略 (" + eventId + ")");
                continue;
            }

            String logId = UUID.randomUUID().toString();
            try {
                saveSimpleLog(eventId, bot.getBotCode(), eventType, sourceId, true, isRedelivery, "RECEIVED",
                        objectMapper.writeValueAsString(event), null, logId);
            } catch (DuplicateKeyException dupEx) {
                // 併發情況下交由資料庫 UNIQUE 約束擋下重複事件
                continue;
            } catch (Exception jsonEx) {
                saveSimpleLog(eventId, bot.getBotCode(), eventType, sourceId, true, isRedelivery, "RECEIVED", rawPayload, null, logId);
            }

            asyncWebhookExecutor.processEventAsync(logId, bot, event);
        }
    }

    private BotAccount resolveBot(String botCodeParam, String destination) {
        if (botCodeParam != null && !botCodeParam.isBlank()) {
            return botAccountMapper.selectOne(
                    new LambdaQueryWrapper<BotAccount>()
                            .eq(BotAccount::getBotCode, botCodeParam.trim())
                            .or()
                            .eq(BotAccount::getLineBotUserId, botCodeParam.trim())
            );
        }
        if (destination != null && !destination.isBlank()) {
            return botAccountMapper.selectOne(
                    new LambdaQueryWrapper<BotAccount>().eq(BotAccount::getLineBotUserId, destination.trim())
            );
        }
        return null;
    }

    public boolean verifySignature(byte[] rawBody, String channelSecret, String signature) {
        if (channelSecret == null || channelSecret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(channelSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = Base64.getEncoder().encodeToString(mac.doFinal(rawBody));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isDuplicateEvent(String webhookEventId) {
        Long count = webhookEventLogMapper.selectCount(
                new LambdaQueryWrapper<WebhookEventLog>().eq(WebhookEventLog::getWebhookEventId, webhookEventId)
        );
        return count != null && count > 0;
    }

    @SuppressWarnings("unchecked")
    private String extractSourceId(Map<String, Object> event) {
        Object sourceObj = event.get("source");
        if (!(sourceObj instanceof Map<?, ?> source)) {
            return null;
        }
        if (source.get("groupId") != null) return source.get("groupId").toString();
        if (source.get("roomId") != null) return source.get("roomId").toString();
        if (source.get("userId") != null) return source.get("userId").toString();
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean extractRedelivery(Map<String, Object> event) {
        Object ctxObj = event.get("deliveryContext");
        if (ctxObj instanceof Map<?, ?> ctx && ctx.get("isRedelivery") instanceof Boolean flag) {
            return flag;
        }
        return false;
    }

    private void saveSimpleLog(
            String webhookEventId,
            String botCode,
            String eventType,
            String sourceId,
            boolean signatureValid,
            boolean isRedelivery,
            String processStatus,
            String rawPayload,
            String errorMessage) {
        saveSimpleLog(webhookEventId, botCode, eventType, sourceId, signatureValid, isRedelivery, processStatus, rawPayload, errorMessage, UUID.randomUUID().toString());
    }

    private void saveSimpleLog(
            String webhookEventId,
            String botCode,
            String eventType,
            String sourceId,
            boolean signatureValid,
            boolean isRedelivery,
            String processStatus,
            String rawPayload,
            String errorMessage,
            String logId) {
        WebhookEventLog entity = new WebhookEventLog();
        entity.setId(logId);
        entity.setWebhookEventId(webhookEventId);
        entity.setBotCode(botCode);
        entity.setEventType(eventType);
        entity.setSourceId(sourceId);
        entity.setSignatureValid(signatureValid ? 1 : 0);
        entity.setIsRedelivery(isRedelivery ? 1 : 0);
        entity.setProcessStatus(processStatus);
        entity.setRawPayload(rawPayload);
        entity.setErrorMessage(errorMessage);
        entity.setCreatedAt(LocalDateTime.now());
        webhookEventLogMapper.insert(entity);
    }
}
