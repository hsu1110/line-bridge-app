package com.ytec.linebridge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ytec.linebridge.dto.ApiClientSaveRequest;
import com.ytec.linebridge.entity.ApiClient;
import com.ytec.linebridge.exception.BusinessException;
import com.ytec.linebridge.mapper.ApiClientMapper;
import com.ytec.linebridge.mapper.UpdateUtils;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部呼叫端系統 API Key 與權限控管端點
 */
@RestController
@RequestMapping("/api/admin/clients")
public class AdminApiClientController {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ApiClientMapper apiClientMapper;

    public AdminApiClientController(ApiClientMapper apiClientMapper) {
        this.apiClientMapper = apiClientMapper;
    }

    @GetMapping
    public List<ApiClient> list() {
        return apiClientMapper.selectList(
                new LambdaQueryWrapper<ApiClient>().orderByAsc(ApiClient::getCreatedAt)
        );
    }

    @PostMapping
    public ApiClient create(@Valid @RequestBody ApiClientSaveRequest request) {
        String code = request.getClientCode().trim();
        ensureUniqueCode(code, null);

        LocalDateTime now = LocalDateTime.now();
        ApiClient client = new ApiClient();
        client.setId(UUID.randomUUID().toString());
        client.setClientCode(code);
        client.setClientName(request.getClientName().trim());
        client.setApiKey(generateApiKey());
        client.setAllowedBotCodes(trimOrNull(request.getAllowedBotCodes()));
        client.setAllowedTemplateCodes(trimOrNull(request.getAllowedTemplateCodes()));
        client.setRateLimitPerMin(request.getRateLimitPerMin() != null ? request.getRateLimitPerMin() : 60);
        client.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        client.setCreatedAt(now);
        client.setUpdatedAt(now);
        apiClientMapper.insert(client);
        return client;
    }

    @PutMapping("/{id}")
    public ApiClient update(@PathVariable String id, @Valid @RequestBody ApiClientSaveRequest request) {
        requireClient(id);
        String code = request.getClientCode().trim();
        ensureUniqueCode(code, id);

        LambdaUpdateWrapper<ApiClient> update = new LambdaUpdateWrapper<ApiClient>()
                .eq(ApiClient::getId, id)
                .set(ApiClient::getClientCode, code)
                .set(ApiClient::getClientName, request.getClientName().trim())
                .set(ApiClient::getUpdatedAt, LocalDateTime.now());

        UpdateUtils.setNullableString(update, ApiClient::getAllowedBotCodes, "ALLOWED_BOT_CODES", trimOrNull(request.getAllowedBotCodes()));
        UpdateUtils.setNullableString(update, ApiClient::getAllowedTemplateCodes, "ALLOWED_TEMPLATE_CODES", trimOrNull(request.getAllowedTemplateCodes()));

        if (request.getRateLimitPerMin() != null) {
            update.set(ApiClient::getRateLimitPerMin, request.getRateLimitPerMin());
        }
        if (request.getEnabled() != null) {
            update.set(ApiClient::getEnabled, request.getEnabled());
        }
        apiClientMapper.update(null, update);
        return requireClient(id);
    }

    @PostMapping("/{id}/rotate-key")
    public ApiClient rotateKey(@PathVariable String id) {
        ApiClient existing = requireClient(id);
        existing.setApiKey(generateApiKey());
        existing.setUpdatedAt(LocalDateTime.now());
        apiClientMapper.updateById(existing);
        return existing;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        requireClient(id);
        apiClientMapper.deleteById(id);
        return Map.of("success", true);
    }

    private ApiClient requireClient(String id) {
        ApiClient client = apiClientMapper.selectById(id);
        if (client == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "找不到 API Client: " + id);
        }
        return client;
    }

    private void ensureUniqueCode(String code, String selfId) {
        LambdaQueryWrapper<ApiClient> wrapper = new LambdaQueryWrapper<ApiClient>().eq(ApiClient::getClientCode, code);
        if (selfId != null) {
            wrapper.ne(ApiClient::getId, selfId);
        }
        Long count = apiClientMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "系統代碼已存在: " + code);
        }
    }

    private String generateApiKey() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return "lbk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String trimOrNull(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        return val.trim();
    }
}
