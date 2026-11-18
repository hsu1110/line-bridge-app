package com.ytec.linebridge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ytec.linebridge.dto.BotAccountView;
import com.ytec.linebridge.dto.BotSaveRequest;
import com.ytec.linebridge.entity.BotAccount;
import com.ytec.linebridge.entity.ContactBot;
import com.ytec.linebridge.exception.BusinessException;
import com.ytec.linebridge.mapper.BotAccountMapper;
import com.ytec.linebridge.mapper.ContactBotMapper;
import com.ytec.linebridge.mapper.UpdateUtils;
import com.ytec.linebridge.service.BotQuotaCacheService;
import com.ytec.linebridge.service.LineApiService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * LINE Bot 官方帳號管理與即時資訊同步端點
 */
@RestController
@RequestMapping("/api/admin/bots")
public class AdminBotController {

    private final BotAccountMapper botAccountMapper;
    private final ContactBotMapper contactBotMapper;
    private final LineApiService lineApiService;
    private final BotQuotaCacheService botQuotaCacheService;

    public AdminBotController(
            BotAccountMapper botAccountMapper,
            ContactBotMapper contactBotMapper,
            LineApiService lineApiService,
            BotQuotaCacheService botQuotaCacheService) {
        this.botAccountMapper = botAccountMapper;
        this.contactBotMapper = contactBotMapper;
        this.lineApiService = lineApiService;
        this.botQuotaCacheService = botQuotaCacheService;
    }

    @GetMapping
    public List<BotAccountView> list() {
        return botAccountMapper.selectList(
                new LambdaQueryWrapper<BotAccount>().orderByAsc(BotAccount::getCreatedAt)
        ).stream().map(BotAccountView::from).toList();
    }

    @PostMapping
    public BotAccountView create(@Valid @RequestBody BotSaveRequest request) {
        if (request.getChannelAccessToken() == null || request.getChannelAccessToken().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "新增機器人必須填寫 Channel Access Token");
        }
        if (request.getChannelSecret() == null || request.getChannelSecret().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "新增機器人必須填寫 Channel Secret");
        }

        String code = request.getBotCode().trim();
        ensureUniqueBotCode(code, null);

        LocalDateTime now = LocalDateTime.now();
        BotAccount bot = new BotAccount();
        bot.setId(UUID.randomUUID().toString());
        bot.setBotCode(code);
        bot.setName(request.getName().trim());
        bot.setLineBotUserId(trimOrNull(request.getLineBotUserId()));
        bot.setLineBasicId(trimOrNull(request.getLineBasicId()));
        bot.setChannelAccessToken(request.getChannelAccessToken().trim());
        bot.setChannelSecret(request.getChannelSecret().trim());
        bot.setQuotaLimit(request.getQuotaLimit() != null ? request.getQuotaLimit() : 200);
        bot.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        bot.setWelcomeEnabled(request.getWelcomeEnabled() != null ? request.getWelcomeEnabled() : 1);
        bot.setWelcomeMessage(trimOrNull(request.getWelcomeMessage()));
        bot.setCreatedAt(now);
        bot.setUpdatedAt(now);
        botAccountMapper.insert(bot);
        return BotAccountView.from(bot);
    }

    @PutMapping("/{id}")
    public BotAccountView update(@PathVariable String id, @Valid @RequestBody BotSaveRequest request) {
        requireBot(id);
        String code = request.getBotCode().trim();
        ensureUniqueBotCode(code, id);

        LambdaUpdateWrapper<BotAccount> update = new LambdaUpdateWrapper<BotAccount>()
                .eq(BotAccount::getId, id)
                .set(BotAccount::getBotCode, code)
                .set(BotAccount::getName, request.getName().trim())
                .set(BotAccount::getUpdatedAt, LocalDateTime.now());

        UpdateUtils.setNullableString(update, BotAccount::getLineBotUserId, "LINE_BOT_USER_ID", trimOrNull(request.getLineBotUserId()));
        UpdateUtils.setNullableString(update, BotAccount::getLineBasicId, "LINE_BASIC_ID", trimOrNull(request.getLineBasicId()));

        if (request.getChannelAccessToken() != null && !request.getChannelAccessToken().isBlank()) {
            update.set(BotAccount::getChannelAccessToken, request.getChannelAccessToken().trim());
        }
        if (request.getChannelSecret() != null && !request.getChannelSecret().isBlank()) {
            update.set(BotAccount::getChannelSecret, request.getChannelSecret().trim());
        }
        if (request.getQuotaLimit() != null) {
            update.set(BotAccount::getQuotaLimit, request.getQuotaLimit());
        }
        if (request.getEnabled() != null) {
            update.set(BotAccount::getEnabled, request.getEnabled());
        }
        if (request.getWelcomeEnabled() != null) {
            update.set(BotAccount::getWelcomeEnabled, request.getWelcomeEnabled());
        }
        if (request.getWelcomeMessage() != null) {
            // 傳入空字串時寫入 SQL NULL，確保能清空並恢復系統預設歡迎詞
            UpdateUtils.setNullableString(update, BotAccount::getWelcomeMessage, "WELCOME_MESSAGE", trimOrNull(request.getWelcomeMessage()));
        }

        botAccountMapper.update(null, update);
        botQuotaCacheService.evict(id);
        return BotAccountView.from(requireBot(id));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable String id) {
        requireBot(id);
        contactBotMapper.delete(new LambdaQueryWrapper<ContactBot>().eq(ContactBot::getBotId, id));
        botAccountMapper.deleteById(id);
        botQuotaCacheService.evict(id);
        return Map.of("success", true);
    }

    /**
     * 一鍵呼叫 LINE GET /v2/bot/info 驗證 Token 並回填 U... 與 @basicId
     */
    @PostMapping("/{id}/sync-info")
    public BotAccountView syncBotInfo(@PathVariable String id) {
        BotAccount bot = requireBot(id);
        Map<String, Object> info;
        try {
            info = lineApiService.getBotInfo(bot.getChannelAccessToken());
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "驗證失敗，無法取得 LINE Bot 資訊: " + e.getMessage());
        }
        if (info == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "LINE API 未回傳 Bot 資訊");
        }

        if (info.get("userId") != null) {
            bot.setLineBotUserId(info.get("userId").toString());
        }
        if (info.get("basicId") != null) {
            bot.setLineBasicId(info.get("basicId").toString());
        }
        if (info.get("displayName") != null && (bot.getName() == null || bot.getName().isBlank())) {
            bot.setName(info.get("displayName").toString());
        }
        bot.setUpdatedAt(LocalDateTime.now());
        botAccountMapper.updateById(bot);
        botQuotaCacheService.evict(id);
        return BotAccountView.from(bot);
    }

    @GetMapping("/{id}/quota")
    public Map<String, Object> getQuota(@PathVariable String id) {
        BotAccount bot = requireBot(id);
        Map<String, Object> quota = lineApiService.getQuota(bot.getChannelAccessToken());
        Map<String, Object> usage = lineApiService.getQuotaConsumption(bot.getChannelAccessToken());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("type", quota.getOrDefault("type", "none"));
        res.put("value", quota.getOrDefault("value", 0));
        res.put("totalUsage", usage.getOrDefault("totalUsage", 0));
        return res;
    }

    private BotAccount requireBot(String id) {
        BotAccount bot = botAccountMapper.selectById(id);
        if (bot == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "找不到機器人: " + id);
        }
        return bot;
    }

    private void ensureUniqueBotCode(String botCode, String selfId) {
        LambdaQueryWrapper<BotAccount> wrapper = new LambdaQueryWrapper<BotAccount>().eq(BotAccount::getBotCode, botCode);
        if (selfId != null) {
            wrapper.ne(BotAccount::getId, selfId);
        }
        Long count = botAccountMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Bot 代碼已存在: " + botCode);
        }
    }

    private String trimOrNull(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        return val.trim();
    }
}
