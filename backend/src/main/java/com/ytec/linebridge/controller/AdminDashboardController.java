package com.ytec.linebridge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ytec.linebridge.entity.BotAccount;
import com.ytec.linebridge.entity.SendLog;
import com.ytec.linebridge.mapper.ApiClientMapper;
import com.ytec.linebridge.mapper.BotAccountMapper;
import com.ytec.linebridge.mapper.ContactMapper;
import com.ytec.linebridge.mapper.SendLogMapper;
import com.ytec.linebridge.mapper.TemplateMapper;
import com.ytec.linebridge.service.BotQuotaCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理儀表板總覽與即時額度統計（採用 SQL 聚合統計與額度並行快取）
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final BotAccountMapper botAccountMapper;
    private final ContactMapper contactMapper;
    private final TemplateMapper templateMapper;
    private final ApiClientMapper apiClientMapper;
    private final SendLogMapper sendLogMapper;
    private final BotQuotaCacheService botQuotaCacheService;

    public AdminDashboardController(
            BotAccountMapper botAccountMapper,
            ContactMapper contactMapper,
            TemplateMapper templateMapper,
            ApiClientMapper apiClientMapper,
            SendLogMapper sendLogMapper,
            BotQuotaCacheService botQuotaCacheService) {
        this.botAccountMapper = botAccountMapper;
        this.contactMapper = contactMapper;
        this.templateMapper = templateMapper;
        this.apiClientMapper = apiClientMapper;
        this.sendLogMapper = sendLogMapper;
        this.botQuotaCacheService = botQuotaCacheService;
    }

    @GetMapping
    public Map<String, Object> summary(@RequestParam(defaultValue = "false") boolean refresh) {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        // 1. 直接由資料庫聚合今日發送數，避免將大量 CLOB 明細載入記憶體
        Map<String, Object> periodStats = sendLogMapper.selectPeriodStats(startOfToday);
        long todayTotal = getLongValue(periodStats, "TODAY_TOTAL");
        long todaySuccess = getLongValue(periodStats, "TODAY_SUCCESS");
        long todayFailed = getLongValue(periodStats, "TODAY_FAILED");

        // 2. SQL 統計今日前 5 名模板使用排行
        List<Map<String, Object>> topRows = sendLogMapper.selectTopTemplateStats(startOfToday);
        List<Map<String, Object>> templateStats = topRows.stream()
                .map(row -> {
                    String name = getStringValue(row, "TEMPLATE_CODE", "DIRECT");
                    long count = getLongValue(row, "USE_COUNT");
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", name);
                    item.put("count", count);
                    item.put("percentage", todayTotal > 0 ? Math.round((count * 100.0) / todayTotal) : 0);
                    return item;
                })
                .toList();

        // 3. 分頁查詢最新 6 筆紀錄
        List<SendLog> recentLogs = sendLogMapper.selectPage(
                new Page<>(1, 6),
                new LambdaQueryWrapper<SendLog>().orderByDesc(SendLog::getCreatedAt)
        ).getRecords();

        // 4. 透過快取與非同步執行緒池並行查詢各 Bot 的即時額度
        List<BotAccount> bots = botAccountMapper.selectList(
                new LambdaQueryWrapper<BotAccount>().orderByAsc(BotAccount::getCreatedAt)
        );
        List<Map<String, Object>> botQuotas = botQuotaCacheService.fetchAllBotQuotas(bots, refresh);

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

    private long getLongValue(Map<String, Object> map, String expectedKey) {
        if (map == null || map.isEmpty()) {
            return 0L;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(expectedKey)) {
                Object val = entry.getValue();
                return val instanceof Number num ? num.longValue() : 0L;
            }
        }
        return 0L;
    }

    private String getStringValue(Map<String, Object> map, String expectedKey, String fallback) {
        if (map == null || map.isEmpty()) {
            return fallback;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(expectedKey)) {
                Object val = entry.getValue();
                return val != null ? val.toString() : fallback;
            }
        }
        return fallback;
    }
}
