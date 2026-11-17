package com.ytec.linebridge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ytec.linebridge.entity.BotAccount;
import com.ytec.linebridge.entity.SendLog;
import com.ytec.linebridge.mapper.ApiClientMapper;
import com.ytec.linebridge.mapper.BotAccountMapper;
import com.ytec.linebridge.mapper.ContactMapper;
import com.ytec.linebridge.mapper.SendLogMapper;
import com.ytec.linebridge.mapper.TemplateMapper;
import com.ytec.linebridge.service.LineApiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理儀表板總覽與即時額度統計
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final BotAccountMapper botAccountMapper;
    private final ContactMapper contactMapper;
    private final TemplateMapper templateMapper;
    private final ApiClientMapper apiClientMapper;
    private final SendLogMapper sendLogMapper;
    private final LineApiService lineApiService;

    public AdminDashboardController(
            BotAccountMapper botAccountMapper,
            ContactMapper contactMapper,
            TemplateMapper templateMapper,
            ApiClientMapper apiClientMapper,
            SendLogMapper sendLogMapper,
            LineApiService lineApiService) {
        this.botAccountMapper = botAccountMapper;
        this.contactMapper = contactMapper;
        this.templateMapper = templateMapper;
        this.apiClientMapper = apiClientMapper;
        this.sendLogMapper = sendLogMapper;
        this.lineApiService = lineApiService;
    }

    @GetMapping
    public Map<String, Object> summary() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        List<SendLog> todayLogs = sendLogMapper.selectList(
                new LambdaQueryWrapper<SendLog>().ge(SendLog::getCreatedAt, startOfToday)
        );

        long todayTotal = todayLogs.size();
        long todaySuccess = todayLogs.stream().filter(l -> "SUCCESS".equals(l.getStatus())).count();
        long todayFailed = todayLogs.stream().filter(l -> "FAILED".equals(l.getStatus())).count();

        // 統計今日各模板使用次數（前 5 名）
        Map<String, Long> templateCounts = new HashMap<>();
        for (SendLog log : todayLogs) {
            String key = (log.getTemplateCode() != null && !log.getTemplateCode().isBlank())
                    ? log.getTemplateCode()
                    : "DIRECT";
            templateCounts.merge(key, 1L, Long::sum);
        }
        List<Map<String, Object>> templateStats = templateCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", entry.getKey());
                    item.put("count", entry.getValue());
                    item.put("percentage", todayTotal > 0 ? Math.round((entry.getValue() * 100.0) / todayTotal) : 0);
                    return item;
                })
                .toList();

        // 使用 MyBatis-Plus 跨版本分頁查詢最新 6 筆紀錄
        List<SendLog> recentLogs = sendLogMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 6),
                new LambdaQueryWrapper<SendLog>().orderByDesc(SendLog::getCreatedAt)
        ).getRecords();

        // 各啟用 Bot 的即時額度查詢
        List<BotAccount> bots = botAccountMapper.selectList(
                new LambdaQueryWrapper<BotAccount>().orderByAsc(BotAccount::getCreatedAt)
        );
        List<Map<String, Object>> botQuotas = bots.stream()
                .sorted(Comparator.comparing(BotAccount::getBotCode))
                .map(bot -> {
                    Map<String, Object> quota = bot.getChannelAccessToken() != null && !bot.getChannelAccessToken().isBlank()
                            ? lineApiService.getQuota(bot.getChannelAccessToken())
                            : Map.of("type", "none", "value", 0);
                    Map<String, Object> usage = bot.getChannelAccessToken() != null && !bot.getChannelAccessToken().isBlank()
                            ? lineApiService.getQuotaConsumption(bot.getChannelAccessToken())
                            : Map.of("totalUsage", 0);

                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", bot.getId());
                    row.put("botCode", bot.getBotCode());
                    row.put("name", bot.getName());
                    row.put("enabled", bot.getEnabled());
                    row.put("type", quota.getOrDefault("type", "none"));
                    row.put("value", quota.getOrDefault("value", bot.getQuotaLimit()));
                    row.put("totalUsage", usage.getOrDefault("totalUsage", 0));
                    return row;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayTotal", todayTotal);
        result.put("todaySuccess", todaySuccess);
        result.put("todayFailed", todayFailed);
        result.put("botCount", bots.size());
        result.put("contactCount", contactMapper.selectCount(null));
        result.put("templateCount", templateMapper.selectCount(null));
        result.put("clientCount", apiClientMapper.selectCount(null));
        result.put("botQuotas", botQuotas);
        result.put("templateStats", templateStats);
        result.put("recentLogs", recentLogs);
        return result;
    }
}
