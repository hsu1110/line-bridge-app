package com.ytec.linebridge.service;

import com.ytec.linebridge.entity.SendLog;
import com.ytec.linebridge.mapper.SendLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 實際執行 LINE Push API 的非同步／同步執行器
 */
@Service
public class AsyncSendExecutor {

    private static final Logger log = LoggerFactory.getLogger(AsyncSendExecutor.class);

    private final LineApiService lineApiService;
    private final SendLogMapper sendLogMapper;

    public AsyncSendExecutor(LineApiService lineApiService, SendLogMapper sendLogMapper) {
        this.lineApiService = lineApiService;
        this.sendLogMapper = sendLogMapper;
    }

    @Async("lineAsyncExecutor")
    public void executeAsync(String logId, String token, String targetLineId, String text, String retryKey) {
        try {
            executeSync(logId, token, targetLineId, text, retryKey);
        } catch (Exception e) {
            log.warn("非同步發送 LINE 訊息失敗 (jobId={}): {}", logId, e.getMessage());
        }
    }

    public void executeSync(String logId, String token, String targetLineId, String text, String retryKey) {
        long start = System.currentTimeMillis();
        SendLog update = new SendLog();
        update.setId(logId);
        try {
            lineApiService.pushTextMessage(token, targetLineId, text, retryKey);
            update.setStatus("SUCCESS");
            update.setDurationMs(System.currentTimeMillis() - start);
            sendLogMapper.updateById(update);
        } catch (Exception e) {
            update.setStatus("FAILED");
            update.setErrorMessage(e.getMessage());
            update.setDurationMs(System.currentTimeMillis() - start);
            sendLogMapper.updateById(update);
            throw e;
        }
    }
}
