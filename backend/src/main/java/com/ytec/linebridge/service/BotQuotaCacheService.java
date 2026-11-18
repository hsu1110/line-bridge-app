package com.ytec.linebridge.service;

import com.ytec.linebridge.config.LineBridgeProperties;
import com.ytec.linebridge.entity.BotAccount;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 儀表板 Bot 額度查詢服務（具備短時 TTL 快取與非同步並行查詢）
 */
@Service
public class BotQuotaCacheService {

    private static final long ERROR_CACHE_MILLIS = 10_000L;

    private final LineApiService lineApiService;
    private final LineBridgeProperties properties;
    private final Executor lineAsyncExecutor;
    private final Map<String, CachedEntry> quotaCache = new ConcurrentHashMap<>();

    public BotQuotaCacheService(
            LineApiService lineApiService,
            LineBridgeProperties properties,
            @Qualifier("lineAsyncExecutor") Executor lineAsyncExecutor) {
        this.lineApiService = lineApiService;
        this.properties = properties;
        this.lineAsyncExecutor = lineAsyncExecutor;
    }

    /**
     * 並行取得各 Bot 的本月 LINE 額度與已用量（支援快取與強制重整）
     */
    public List<Map<String, Object>> fetchAllBotQuotas(List<BotAccount> bots, boolean forceRefresh) {
        if (bots == null || bots.isEmpty()) {
            return List.of();
        }

        List<BotAccount> sortedBots = bots.stream()
                .sorted(Comparator.comparing(BotAccount::getBotCode))
                .toList();

        List<CompletableFuture<Map<String, Object>>> futures = sortedBots.stream()
                .map(bot -> supplyWithFallback(bot, forceRefresh))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    /**
     * 清除指定 Bot 的額度快取（更新 Token 或刪除帳號時呼叫）
     */
    public void evict(String botId) {
        if (botId != null) {
            quotaCache.remove(botId);
        }
    }

    private CompletableFuture<Map<String, Object>> supplyWithFallback(BotAccount bot, boolean forceRefresh) {
        if (!forceRefresh) {
            CachedEntry cached = quotaCache.get(bot.getId());
            if (cached != null && !cached.isExpired()) {
                return CompletableFuture.completedFuture(buildRow(bot, cached.quota(), cached.usage()));
            }
        }
        try {
            return CompletableFuture.supplyAsync(() -> queryAndCache(bot), lineAsyncExecutor);
        } catch (TaskRejectedException ex) {
            return CompletableFuture.completedFuture(queryAndCache(bot));
        }
    }

    private Map<String, Object> queryAndCache(BotAccount bot) {
        String token = bot.getChannelAccessToken();
        if (token == null || token.isBlank()) {
            return buildRow(bot, Map.of("type", "none", "value", 0), Map.of("totalUsage", 0));
        }

        Map<String, Object> quota = lineApiService.getQuota(token);
        Map<String, Object> usage = lineApiService.getQuotaConsumption(token);
        Map<String, Object> safeQuota = quota != null ? quota : Map.of("type", "none", "value", 0);
        Map<String, Object> safeUsage = usage != null ? usage : Map.of("totalUsage", 0);

        boolean isError = "error".equals(safeQuota.get("type"));
        long ttlMillis = isError
                ? ERROR_CACHE_MILLIS
                : Math.max(0, properties.getDashboardQuotaCacheSeconds()) * 1000L;

        if (ttlMillis > 0) {
            quotaCache.put(bot.getId(), new CachedEntry(safeQuota, safeUsage, System.currentTimeMillis() + ttlMillis));
        }
        return buildRow(bot, safeQuota, safeUsage);
    }

    private Map<String, Object> buildRow(BotAccount bot, Map<String, Object> quota, Map<String, Object> usage) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", bot.getId());
        row.put("botCode", bot.getBotCode());
        row.put("name", bot.getName());
        row.put("enabled", bot.getEnabled());
        row.put("type", quota.getOrDefault("type", "none"));
        row.put("value", quota.getOrDefault("value", bot.getQuotaLimit()));
        row.put("totalUsage", usage.getOrDefault("totalUsage", 0));
        return row;
    }

    private record CachedEntry(Map<String, Object> quota, Map<String, Object> usage, long expiresAtMillis) {
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }
}
