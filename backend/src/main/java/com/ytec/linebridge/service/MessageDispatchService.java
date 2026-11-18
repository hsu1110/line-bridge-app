package com.ytec.linebridge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ytec.linebridge.config.LineBridgeProperties;
import com.ytec.linebridge.dto.SendRequest;
import com.ytec.linebridge.dto.SendResponse;
import com.ytec.linebridge.entity.ApiClient;
import com.ytec.linebridge.entity.BotAccount;
import com.ytec.linebridge.entity.Contact;
import com.ytec.linebridge.entity.SendLog;
import com.ytec.linebridge.entity.Template;
import com.ytec.linebridge.exception.BusinessException;
import com.ytec.linebridge.mapper.BotAccountMapper;
import com.ytec.linebridge.mapper.ContactMapper;
import com.ytec.linebridge.mapper.SendLogMapper;
import com.ytec.linebridge.mapper.TemplateMapper;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 訊息發送調度核心服務（含權限校驗、目標綁定檢查、模板參數驗證與佇列追蹤）
 */
@Service
public class MessageDispatchService {

    private static final int LINE_MAX_TEXT_LENGTH = 5000;

    private final BotAccountMapper botAccountMapper;
    private final ContactMapper contactMapper;
    private final TemplateMapper templateMapper;
    private final SendLogMapper sendLogMapper;
    private final ContactBindingService contactBindingService;
    private final TemplateEngineService templateEngineService;
    private final AsyncSendExecutor asyncSendExecutor;
    private final LineBridgeProperties properties;

    public MessageDispatchService(
            BotAccountMapper botAccountMapper,
            ContactMapper contactMapper,
            TemplateMapper templateMapper,
            SendLogMapper sendLogMapper,
            ContactBindingService contactBindingService,
            TemplateEngineService templateEngineService,
            AsyncSendExecutor asyncSendExecutor,
            LineBridgeProperties properties) {
        this.botAccountMapper = botAccountMapper;
        this.contactMapper = contactMapper;
        this.templateMapper = templateMapper;
        this.sendLogMapper = sendLogMapper;
        this.contactBindingService = contactBindingService;
        this.templateEngineService = templateEngineService;
        this.asyncSendExecutor = asyncSendExecutor;
        this.properties = properties;
    }

    /**
     * 處理發送訊息請求
     */
    public SendResponse dispatch(SendRequest request, ApiClient apiClient, String sourceIp, boolean isAdminTest) {
        BotAccount bot = resolveBot(request.getBotCode());
        checkClientPermissions(apiClient, bot.getBotCode(), request.getTemplateCode());

        TargetInfo target = resolveTarget(request.getTargetCode(), request.getTo(), bot, isAdminTest);
        String finalContent = resolveContent(request);
        if (finalContent.length() > LINE_MAX_TEXT_LENGTH) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "訊息長度不可超過 " + LINE_MAX_TEXT_LENGTH + " 字元（目前為 " + finalContent.length() + " 字元）"
            );
        }

        String jobId = UUID.randomUUID().toString();
        String retryKey = UUID.randomUUID().toString();

        SendLog logEntry = new SendLog();
        logEntry.setId(jobId);
        logEntry.setClientId(isAdminTest ? "ADMIN_TEST" : (apiClient != null ? apiClient.getId() : "ANONYMOUS"));
        logEntry.setClientName(isAdminTest ? "管理員測試" : (apiClient != null ? apiClient.getClientName() : "Anonymous"));
        logEntry.setBotId(bot.getId());
        logEntry.setBotCode(bot.getBotCode());
        logEntry.setTargetCode(target.targetCodeForLog());
        logEntry.setTargetLineId(target.lineId());
        logEntry.setTemplateCode(request.getTemplateCode());
        logEntry.setContent(finalContent);
        logEntry.setStatus("QUEUED");
        logEntry.setRetryKey(retryKey);
        logEntry.setSourceIp(sourceIp);
        logEntry.setCreatedAt(LocalDateTime.now());
        sendLogMapper.insert(logEntry);

        boolean runAsync = !Boolean.FALSE.equals(request.getAsync()) && !isAdminTest;
        if (runAsync) {
            try {
                asyncSendExecutor.executeAsync(jobId, bot.getChannelAccessToken(), target.lineId(), finalContent, retryKey);
                return SendResponse.queued(jobId, finalContent);
            } catch (TaskRejectedException ex) {
                // 非同步佇列已滿時立即更新紀錄為 FAILED，避免任務永遠卡在 QUEUED
                SendLog failed = new SendLog();
                failed.setId(jobId);
                failed.setStatus("FAILED");
                failed.setErrorMessage("非同步發送佇列已滿，請求被拒絕");
                sendLogMapper.updateById(failed);
                throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "目前發送佇列忙碌中，請稍後再試");
            }
        } else {
            try {
                asyncSendExecutor.executeSync(jobId, bot.getChannelAccessToken(), target.lineId(), finalContent, retryKey);
                return SendResponse.sent(jobId, finalContent);
            } catch (Exception e) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "發送失敗: " + e.getMessage());
            }
        }
    }

    public SendLog getJob(String jobId, ApiClient apiClient) {
        SendLog log = sendLogMapper.selectById(jobId);
        if (log == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "找不到指定的發送任務: " + jobId);
        }
        if (apiClient != null && !apiClient.getId().equals(log.getClientId())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "無權查詢其他系統的發送任務");
        }
        return log;
    }

    private BotAccount resolveBot(String botCode) {
        if (botCode == null || botCode.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "缺少 bot_code 參數");
        }
        String key = botCode.trim();
        BotAccount bot = botAccountMapper.selectOne(
                new LambdaQueryWrapper<BotAccount>()
                        .eq(BotAccount::getBotCode, key)
                        .or()
                        .eq(BotAccount::getLineBotUserId, key)
                        .or()
                        .eq(BotAccount::getId, key)
        );
        if (bot == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "找不到指定的機器人: " + botCode);
        }
        if (bot.getEnabled() == null || bot.getEnabled() != 1) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "機器人已停用: " + bot.getBotCode());
        }
        if (bot.getChannelAccessToken() == null || bot.getChannelAccessToken().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "機器人尚未設定 Channel Access Token");
        }
        return bot;
    }

    private void checkClientPermissions(ApiClient client, String botCode, String templateCode) {
        if (client == null) {
            return;
        }
        if (!isAllowed(client.getAllowedBotCodes(), botCode)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "此 API Key 無權使用機器人: " + botCode);
        }
        if (templateCode != null && !templateCode.isBlank()
                && !isAllowed(client.getAllowedTemplateCodes(), templateCode)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "此 API Key 無權使用模板: " + templateCode);
        }
    }

    private boolean isAllowed(String allowListStr, String value) {
        if (allowListStr == null || allowListStr.isBlank()) {
            return true;
        }
        Set<String> allowed = Arrays.stream(allowListStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return allowed.isEmpty() || allowed.contains(value);
    }

    private TargetInfo resolveTarget(String targetCode, String to, BotAccount bot, boolean isAdminTest) {
        if (targetCode != null && !targetCode.isBlank()) {
            Contact contact = contactMapper.selectOne(
                    new LambdaQueryWrapper<Contact>().eq(Contact::getCode, targetCode.trim())
            );
            if (contact == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "找不到目標代碼: " + targetCode);
            }
            if (properties.isStrictBindingCheck() && !isAdminTest
                    && !contactBindingService.isBound(contact.getId(), bot.getId())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "目標 [" + targetCode + "] 尚未綁定機器人 [" + bot.getBotCode() + "]"
                );
            }
            return new TargetInfo(contact.getLineId(), contact.getCode());
        }

        if (to != null && !to.isBlank()) {
            String lineId = to.trim();
            Contact existing = contactMapper.selectOne(
                    new LambdaQueryWrapper<Contact>().eq(Contact::getLineId, lineId)
            );
            if (existing != null && properties.isStrictBindingCheck() && !isAdminTest
                    && !contactBindingService.isBound(existing.getId(), bot.getId())) {
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "LINE ID [" + lineId + "] 尚未綁定機器人 [" + bot.getBotCode() + "]"
                );
            }
            return new TargetInfo(lineId, existing != null && existing.getCode() != null ? existing.getCode() : "DIRECT");
        }

        throw new BusinessException(HttpStatus.BAD_REQUEST, "請提供 target_code 或 to");
    }

    private String resolveContent(SendRequest request) {
        if (request.getTemplateCode() != null && !request.getTemplateCode().isBlank()) {
            Template template = templateMapper.selectOne(
                    new LambdaQueryWrapper<Template>().eq(Template::getCode, request.getTemplateCode().trim())
            );
            if (template == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "找不到模板代碼: " + request.getTemplateCode());
            }
            return templateEngineService.renderStrict(template.getContent(), request.getParams());
        }
        if (request.getMessage() != null && !request.getMessage().isBlank()) {
            return request.getMessage();
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "請提供 template_code 或 message");
    }

    private record TargetInfo(String lineId, String targetCodeForLog) {
    }
}
