package com.ytec.linebridge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytec.linebridge.config.LineBridgeProperties;
import com.ytec.linebridge.entity.BotAccount;
import com.ytec.linebridge.entity.WebhookEventLog;
import com.ytec.linebridge.exception.BusinessException;
import com.ytec.linebridge.mapper.BotAccountMapper;
import com.ytec.linebridge.mapper.WebhookEventLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LINE Webhook 接收服務（負責防灌水限流、Payload 截斷、Raw Body 驗簽、去重與除錯紀錄）
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final int MAX_TRACKED_IPS = 2000;

    private final BotAccountMapper botAccountMapper;
    private final WebhookEventLogMapper webhookEventLogMapper;
    private final AsyncWebhookExecutor asyncWebhookExecutor;
    private final ObjectMapper objectMapper;
    private final LineBridgeProperties properties;
    private final Map<String, Deque<Long>> failedIpWindows = new ConcurrentHashMap<>();

    public WebhookService(
            BotAccountMapper botAccountMapper,
            WebhookEventLogMapper webhookEventLogMapper,
            AsyncWebhookExecutor asyncWebhookExecutor,
            ObjectMapper objectMapper,
            LineBridgeProperties properties) {
        this.botAccountMapper = botAccountMapper;
        this.webhookEventLogMapper = webhookEventLogMapper;
        this.asyncWebhookExecutor = asyncWebhookExecutor;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void handleWebhook(String botCodeParam, byte[] rawBody, String signature) {
        handleWebhook(botCodeParam, rawBody, signature, "unknown");
    }

    @SuppressWarnings("unchecked")
    public void handleWebhook(String botCodeParam, byte[] rawBody, String signature, String sourceIp) {
        byte[] safeBody = rawBody != null ? rawBody : new byte[0];
        int maxBodyBytes = properties.getWebhook().getMaxBodyBytes();
        if (maxBodyBytes > 0 && safeBody.length > maxBodyBytes) {
            String preview = truncatePayload(
                    new String(safeBody, 0, Math.min(safeBody.length, 4096), StandardCharsets.UTF_8),
                    properties.getWebhook().getFailedPayloadLogMaxChars()
            );
            rejectAndLogInvalid(
                    sourceIp,
                    botCodeParam,
                    "payload_too_large",
                    null,
                    preview,
                    "Webhook Body 大小 (" + safeBody.length + " bytes) 超過上限",
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Payload Too Large"
            );
        }

        String rawPayload = new String(safeBody, StandardCharsets.UTF_8);
        Map<String, Object> payloadMap;
        try {
            payloadMap = objectMapper.readValue(safeBody, new TypeReference<>() {});
        } catch (Exception e) {
            rejectAndLogInvalid(
                    sourceIp,
                    botCodeParam,
                    "invalid_json",
                    null,
                    rawPayload,
                    "JSON 解析失敗: " + e.getMessage(),
                    HttpStatus.BAD_REQUEST,
                    "無效的 Webhook JSON 格式"
            );
            return;
        }

        String destination = payloadMap.get("destination") != null ? payloadMap.get("destination").toString() : null;
        BotAccount bot = resolveBot(botCodeParam, destination);

        if (bot == null) {
            rejectAndLogInvalid(
                    sourceIp,
                    botCodeParam != null ? botCodeParam : destination,
                    "unknown_bot",
                    destination,
                    rawPayload,
                    "找不到對應的機器人設定",
                    HttpStatus.FORBIDDEN,
                    "Forbidden"
            );
            return;
        }

        if (bot.getEnabled() == null || bot.getEnabled() != 1) {
            rejectAndLogInvalid(
                    sourceIp,
                    bot.getBotCode(),
                    "bot_disabled",
                    destination,
                    rawPayload,
                    "機器人已停用",
                    HttpStatus.FORBIDDEN,
                    "Forbidden"
            );
            return;
        }

        // 先驗證 X-Line-Signature 再回填 lineBotUserId，避免未授權請求篡改資料
        if (!verifySignature(safeBody, bot.getChannelSecret(), signature)) {
            rejectAndLogInvalid(
                    sourceIp,
                    bot.getBotCode(),
                    "invalid_signature",
                    destination,
                    rawPayload,
                    "X-Line-Signature 驗證失敗 (IP: " + normalizeIp(sourceIp) + ")",
                    HttpStatus.FORBIDDEN,
                    "Invalid Signature"
            );
            return;
        }

        // 驗簽通過且尚未綁定 LINE Bot User ID 時，自動回填 destination 便於日後比對
        if ((bot.getLineBotUserId() == null || bot.getLineBotUserId().isBlank()) && destination != null && !destination.isBlank()) {
            BotAccount patch = new BotAccount();
            patch.setId(bot.getId());
            patch.setLineBotUserId(destination);
            patch.setUpdatedAt(LocalDateTime.now());
            botAccountMapper.updateById(patch);
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

            try {
                asyncWebhookExecutor.processEventAsync(logId, bot, event);
            } catch (TaskRejectedException rejectedEx) {
                log.warn("Webhook 非同步佇列已滿，無法立即處理事件 (logId={}, bot={})", logId, bot.getBotCode());
                WebhookEventLog failPatch = new WebhookEventLog();
                failPatch.setId(logId);
                failPatch.setProcessStatus("FAILED");
                failPatch.setErrorMessage("Webhook 非同步處理佇列已滿");
                webhookEventLogMapper.updateById(failPatch);
            }
        }
    }

    /**
     * 統一處理無效 / 未驗簽通過的 Webhook：依來源 IP 做失敗限流、截斷寫入內容，防止 DB 被惡意掃描灌滿
     */
    private void rejectAndLogInvalid(
            String sourceIp,
            String botCode,
            String eventType,
            String sourceId,
            String rawPayload,
            String errorMessage,
            HttpStatus status,
            String responseMessage) {
        String ipKey = normalizeIp(sourceIp);
        if (isInvalidRateLimited(ipKey)) {
            log.warn("來源 IP [{}] 短時間內過多無效 Webhook 請求 ({})，已略過寫庫", ipKey, eventType);
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "Too Many Invalid Requests");
        }

        int maxChars = properties.getWebhook().getFailedPayloadLogMaxChars();
        String truncated = truncatePayload(rawPayload, maxChars);
        saveSimpleLog(null, botCode, eventType, sourceId, false, false, "FAILED", truncated, errorMessage);
        throw new BusinessException(status, responseMessage);
    }

    private boolean isInvalidRateLimited(String ipKey) {
        int limit = properties.getWebhook().getFailedRateLimitPerMin();
        if (limit <= 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        long cutoff = now - 60_000L;
        if (failedIpWindows.size() > MAX_TRACKED_IPS) {
            cleanupExpiredIpWindows(cutoff);
        }

        Deque<Long> window = failedIpWindows.computeIfAbsent(ipKey, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() < cutoff) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                return true;
            }
            window.addLast(now);
            return false;
        }
    }

    private void cleanupExpiredIpWindows(long cutoff) {
        failedIpWindows.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                    deque.pollFirst();
                }
                return deque.isEmpty();
            }
        });
    }

    private String truncatePayload(String payload, int maxChars) {
        if (payload == null) {
            return null;
        }
        if (maxChars <= 0 || payload.length() <= maxChars) {
            return payload;
        }
        return payload.substring(0, maxChars) + "\n...(已截斷，總長度 " + payload.length() + " 字元)";
    }

    private String normalizeIp(String sourceIp) {
        return (sourceIp == null || sourceIp.isBlank()) ? "unknown" : sourceIp.trim();
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
