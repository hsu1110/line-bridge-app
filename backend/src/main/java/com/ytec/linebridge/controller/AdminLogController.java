package com.ytec.linebridge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ytec.linebridge.entity.SendLog;
import com.ytec.linebridge.entity.WebhookEventLog;
import com.ytec.linebridge.mapper.SendLogMapper;
import com.ytec.linebridge.mapper.WebhookEventLogMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 發送紀錄與 Webhook 除錯紀錄分頁查詢端點
 */
@RestController
@RequestMapping("/api/admin")
public class AdminLogController {

    private final SendLogMapper sendLogMapper;
    private final WebhookEventLogMapper webhookEventLogMapper;

    public AdminLogController(SendLogMapper sendLogMapper, WebhookEventLogMapper webhookEventLogMapper) {
        this.sendLogMapper = sendLogMapper;
        this.webhookEventLogMapper = webhookEventLogMapper;
    }

    @GetMapping("/logs")
    public Page<SendLog> listSendLogs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String botCode,
            @RequestParam(required = false) String keyword) {

        LambdaQueryWrapper<SendLog> wrapper = new LambdaQueryWrapper<SendLog>()
                .orderByDesc(SendLog::getCreatedAt);

        if (status != null && !status.isBlank()) {
            wrapper.eq(SendLog::getStatus, status.trim());
        }
        if (botCode != null && !botCode.isBlank()) {
            wrapper.eq(SendLog::getBotCode, botCode.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SendLog::getTargetCode, kw)
                    .or().like(SendLog::getTemplateCode, kw)
                    .or().like(SendLog::getClientName, kw));
        }

        return sendLogMapper.selectPage(new Page<>(page, Math.min(size, 100)), wrapper);
    }

    @GetMapping("/webhooks")
    public Page<WebhookEventLog> listWebhooks(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String botCode,
            @RequestParam(required = false) String processStatus) {

        LambdaQueryWrapper<WebhookEventLog> wrapper = new LambdaQueryWrapper<WebhookEventLog>()
                .orderByDesc(WebhookEventLog::getCreatedAt);

        if (botCode != null && !botCode.isBlank()) {
            wrapper.eq(WebhookEventLog::getBotCode, botCode.trim());
        }
        if (processStatus != null && !processStatus.isBlank()) {
            wrapper.eq(WebhookEventLog::getProcessStatus, processStatus.trim());
        }

        return webhookEventLogMapper.selectPage(new Page<>(page, Math.min(size, 100)), wrapper);
    }
}
