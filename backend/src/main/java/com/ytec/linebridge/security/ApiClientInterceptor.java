package com.ytec.linebridge.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ytec.linebridge.config.LineBridgeProperties;
import com.ytec.linebridge.entity.ApiClient;
import com.ytec.linebridge.mapper.ApiClientMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 外部呼叫端 API Key 驗證與每分鐘速率限制攔截器
 */
@Component
public class ApiClientInterceptor implements HandlerInterceptor {

    public static final String ATTR_API_CLIENT = "apiClient";
    private final ApiClientMapper apiClientMapper;
    private final LineBridgeProperties properties;
    private final Map<String, Deque<Long>> rateWindows = new ConcurrentHashMap<>();

    public ApiClientInterceptor(ApiClientMapper apiClientMapper, LineBridgeProperties properties) {
        this.apiClientMapper = apiClientMapper;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String providedKey = request.getHeader("x-api-key");
        if (providedKey == null || providedKey.isBlank()) {
            providedKey = request.getParameter("api_key");
        }

        if (providedKey == null || providedKey.isBlank()) {
            if (properties.isAllowAnonymousSend()) {
                return true;
            }
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "缺少 API Key (請於 Header 帶入 x-api-key)");
            return false;
        }

        ApiClient client = apiClientMapper.selectOne(
                new LambdaQueryWrapper<ApiClient>().eq(ApiClient::getApiKey, providedKey.trim())
        );

        if (client == null || client.getEnabled() == null || client.getEnabled() != 1) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "無效或已停用的 API Key");
            return false;
        }

        if (isRateLimited(client)) {
            writeError(response, 429, "超過每分鐘呼叫上限 (" + client.getRateLimitPerMin() + " 次/分)");
            return false;
        }

        request.setAttribute(ATTR_API_CLIENT, client);
        return true;
    }

    private boolean isRateLimited(ApiClient client) {
        Integer limit = client.getRateLimitPerMin();
        if (limit == null || limit <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000L;
        Deque<Long> window = rateWindows.computeIfAbsent(client.getId(), k -> new ArrayDeque<>());
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

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"error\":\"" + escapeJson(message) + "\"}");
    }

    private String escapeJson(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
