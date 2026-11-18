package com.ytec.linebridge.service;

import com.ytec.linebridge.config.LineBridgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 封裝 LINE Messaging API HTTP 呼叫（採用 JDK HttpClient 連線池與 HTTP/2、Retry-Key 及指數退避重試）
 */
@Service
public class LineApiService {

    private static final Logger log = LoggerFactory.getLogger(LineApiService.class);
    private static final int MAX_PUSH_ATTEMPTS = 3;

    private final RestClient restClient;

    public LineApiService(LineBridgeProperties properties) {
        // 使用內建連線池且支援 HTTP/2 的 JDK HttpClient，降低高頻推播時重複建立 TLS 連線的開銷
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getLineApiBase())
                .requestFactory(factory)
                .build();
    }

    /**
     * 主動推播文字訊息（帶 X-Line-Retry-Key 確保網路重試不重複扣款）
     */
    public Map<String, Object> pushTextMessage(String channelAccessToken, String to, String text, String retryKey) {
        Map<String, Object> body = Map.of(
                "to", to,
                "messages", List.of(Map.of("type", "text", "text", text))
        );

        RestClientException lastException = null;
        for (int attempt = 1; attempt <= MAX_PUSH_ATTEMPTS; attempt++) {
            try {
                return restClient.post()
                        .uri("/message/push")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + channelAccessToken)
                        .header("X-Line-Retry-Key", retryKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
            } catch (HttpClientErrorException e) {
                // 4xx（除 429 限流外）代表權杖或目標錯誤，不可重試
                if (e.getStatusCode().value() == 409) {
                    // 409 Conflict 表示相同 Retry-Key 之前已投遞成功
                    return Map.of("conflict", true);
                }
                if (e.getStatusCode().value() != 429) {
                    throw new IllegalStateException("LINE API 回傳錯誤 (" + e.getStatusCode().value() + "): " + e.getResponseBodyAsString(), e);
                }
                lastException = e;
            } catch (RestClientException e) {
                lastException = e;
            }

            if (attempt < MAX_PUSH_ATTEMPTS) {
                sleepBackoff(attempt);
            }
        }

        throw new IllegalStateException("LINE Push API 重試失敗: "
                + (lastException != null ? lastException.getMessage() : "未知錯誤"), lastException);
    }

    /**
     * 使用免費 Reply API 回應 Webhook 事件
     */
    public void replyTextMessage(String channelAccessToken, String replyToken, String text) {
        if (replyToken == null || replyToken.isBlank()) {
            return;
        }
        Map<String, Object> body = Map.of(
                "replyToken", replyToken,
                "messages", List.of(Map.of("type", "text", "text", text))
        );
        try {
            restClient.post()
                    .uri("/message/reply")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + channelAccessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Reply API 呼叫失敗（可能 replyToken 已過期）: {}", e.getMessage());
        }
    }

    /**
     * 取得使用者基本資料
     */
    public Map<String, Object> getProfile(String channelAccessToken, String userId) {
        try {
            return restClient.get()
                    .uri("/profile/{userId}", userId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + channelAccessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("取得 LINE 使用者資料失敗 (userId={}): {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 取得群組名稱摘要
     */
    public Map<String, Object> getGroupSummary(String channelAccessToken, String groupId) {
        try {
            return restClient.get()
                    .uri("/group/{groupId}/summary", groupId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + channelAccessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("取得 LINE 群組摘要失敗 (groupId={}): {}", groupId, e.getMessage());
            return null;
        }
    }

    /**
     * 取得 Bot 官方帳號資訊（自動取得正確的 U... userId 與 @basicId）
     */
    public Map<String, Object> getBotInfo(String channelAccessToken) {
        return restClient.get()
                .uri("/info")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + channelAccessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    /**
     * 查詢本月額度上限
     */
    public Map<String, Object> getQuota(String channelAccessToken) {
        try {
            return restClient.get()
                    .uri("/message/quota")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + channelAccessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("查詢 LINE Quota 失敗: {}", e.getMessage());
            return Map.of("type", "error", "value", 0);
        }
    }

    /**
     * 查詢本月已使用訊息數
     */
    public Map<String, Object> getQuotaConsumption(String channelAccessToken) {
        try {
            return restClient.get()
                    .uri("/message/quota/consumption")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + channelAccessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (Exception e) {
            log.warn("查詢 LINE Quota Consumption 失敗: {}", e.getMessage());
            return Map.of("totalUsage", 0);
        }
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(300L * attempt);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
