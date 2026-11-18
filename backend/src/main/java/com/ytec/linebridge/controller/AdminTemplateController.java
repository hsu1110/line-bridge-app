package com.ytec.linebridge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ytec.linebridge.dto.SendRequest;
import com.ytec.linebridge.dto.SendResponse;
import com.ytec.linebridge.dto.TemplatePreviewRequest;
import com.ytec.linebridge.dto.TemplateSaveRequest;
import com.ytec.linebridge.entity.Template;
import com.ytec.linebridge.exception.BusinessException;
import com.ytec.linebridge.mapper.TemplateMapper;
import com.ytec.linebridge.mapper.UpdateUtils;
import com.ytec.linebridge.service.MessageDispatchService;
import com.ytec.linebridge.service.TemplateEngineService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 訊息模板管理、即時預覽渲染與管理員測試發送端點
 */
@RestController
@RequestMapping("/api/admin/templates")
public class AdminTemplateController {

    private final TemplateMapper templateMapper;
    private final TemplateEngineService templateEngineService;
    private final MessageDispatchService messageDispatchService;

    public AdminTemplateController(
            TemplateMapper templateMapper,
            TemplateEngineService templateEngineService,
            MessageDispatchService messageDispatchService) {
        this.templateMapper = templateMapper;
        this.templateEngineService = templateEngineService;
        this.messageDispatchService = messageDispatchService;
    }

    @GetMapping
    public List<Template> list() {
        return templateMapper.selectList(
                new LambdaQueryWrapper<Template>().orderByAsc(Template::getCode)
        );
    }

    @PostMapping
    public Template create(@Valid @RequestBody TemplateSaveRequest request) {
        String code = request.getCode().trim();
        ensureUniqueCode(code, null);

        LocalDateTime now = LocalDateTime.now();
        Template template = new Template();
        template.setId(UUID.randomUUID().toString());
        template.setCode(code);
        template.setName(request.getName().trim());
        template.setContent(request.getContent());
        template.setRequiredVars(templateEngineService.serializeVariables(
                templateEngineService.extractVariables(request.getContent())
        ));
        template.setDescription(request.getDescription());
        template.setCreatedAt(now);
        template.setUpdatedAt(now);
        templateMapper.insert(template);
        return template;
    }

    @PutMapping("/{id}")
    public Template update(@PathVariable String id, @Valid @RequestBody TemplateSaveRequest request) {
        Template existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "找不到模板: " + id);
        }

        String code = request.getCode().trim();
        ensureUniqueCode(code, id);

        String requiredVars = templateEngineService.serializeVariables(
                templateEngineService.extractVariables(request.getContent())
        );
        String desc = (request.getDescription() == null || request.getDescription().isBlank())
                ? null
                : request.getDescription().trim();

        LambdaUpdateWrapper<Template> update = new LambdaUpdateWrapper<Template>()
                .eq(Template::getId, id)
                .set(Template::getCode, code)
                .set(Template::getName, request.getName().trim())
                .set(Template::getContent, request.getContent())
                .set(Template::getRequiredVars, requiredVars)
                .set(Template::getUpdatedAt, LocalDateTime.now());
        UpdateUtils.setNullableString(update, Template::getDescription, "DESCRIPTION", desc);
        templateMapper.update(null, update);
        return templateMapper.selectById(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        Template existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "找不到模板: " + id);
        }
        templateMapper.deleteById(id);
        return Map.of("success", true);
    }

    /**
     * Dry-Run 預覽渲染結果（不實際發送、不扣 LINE 額度）
     */
    @PostMapping("/preview")
    public TemplateEngineService.PreviewResult preview(@RequestBody TemplatePreviewRequest request) {
        return templateEngineService.preview(request.getContent(), request.getParams());
    }

    /**
     * 管理員同步測試發送（直接回傳 LINE API 成敗與實際訊息內容）
     */
    @PostMapping("/test-send")
    public SendResponse testSend(@RequestBody SendRequest request, HttpServletRequest httpRequest) {
        request.setAsync(false);
        return messageDispatchService.dispatch(request, null, httpRequest.getRemoteAddr(), true);
    }

    private void ensureUniqueCode(String code, String selfId) {
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<Template>().eq(Template::getCode, code);
        if (selfId != null) {
            wrapper.ne(Template::getId, selfId);
        }
        Long count = templateMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "模板代碼已存在: " + code);
        }
    }
}
