package com.ytec.linebridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytec.linebridge.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 訊息模板變數解析、必填驗證與安全渲染引擎
 */
@Service
public class TemplateEngineService {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private final ObjectMapper objectMapper;

    public TemplateEngineService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 從模板內文抽取不重複變數名稱（依出現順序保留）
     */
    public List<String> extractVariables(String content) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        Set<String> vars = new LinkedHashSet<>();
        Matcher matcher = VAR_PATTERN.matcher(content);
        while (matcher.find()) {
            vars.add(matcher.group(1));
        }
        return new ArrayList<>(vars);
    }

    public String serializeVariables(List<String> vars) {
        try {
            return objectMapper.writeValueAsString(vars);
        } catch (Exception e) {
            return "[]";
        }
    }

    public List<String> parseVariables(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 嚴格渲染模板：若有缺少的變數則拋出 400 例外，避免發送殘留 {{var}} 的壞訊息
     */
    public String renderStrict(String content, Map<String, Object> params) {
        List<String> requiredVars = extractVariables(content);
        List<String> missing = new ArrayList<>();
        for (String var : requiredVars) {
            if (params == null || !params.containsKey(var) || params.get(var) == null) {
                missing.add(var);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "缺少模板必填參數: " + String.join(", ", missing));
        }
        return replacePlaceholders(content, params);
    }

    /**
     * 預覽渲染（不中斷執行，同時回傳缺少變數清單給管理員預覽用）
     */
    public PreviewResult preview(String content, Map<String, Object> params) {
        List<String> requiredVars = extractVariables(content);
        List<String> missing = new ArrayList<>();
        for (String var : requiredVars) {
            if (params == null || !params.containsKey(var) || params.get(var) == null || params.get(var).toString().isEmpty()) {
                missing.add(var);
            }
        }
        String rendered = replacePlaceholders(content != null ? content : "", params);
        return new PreviewResult(requiredVars, missing, rendered);
    }

    private String replacePlaceholders(String content, Map<String, Object> params) {
        if (content == null || content.isEmpty() || params == null || params.isEmpty()) {
            return content;
        }
        Matcher matcher = VAR_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            Object val = params.get(key);
            String replacement = val != null ? val.toString() : matcher.group(0);
            // 使用 quoteReplacement 防止參數內容含 $ 或反斜線造成錯亂
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public record PreviewResult(List<String> variables, List<String> missingVariables, String renderedContent) {
    }
}
