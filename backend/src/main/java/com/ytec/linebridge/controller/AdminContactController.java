package com.ytec.linebridge.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ytec.linebridge.dto.ContactSaveRequest;
import com.ytec.linebridge.dto.ContactView;
import com.ytec.linebridge.entity.Contact;
import com.ytec.linebridge.entity.ContactBot;
import com.ytec.linebridge.exception.BusinessException;
import com.ytec.linebridge.mapper.ContactBotMapper;
import com.ytec.linebridge.mapper.ContactMapper;
import com.ytec.linebridge.service.ContactBindingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 通訊錄聯絡人與群組管理端點
 */
@RestController
@RequestMapping("/api/admin/contacts")
public class AdminContactController {

    private final ContactMapper contactMapper;
    private final ContactBotMapper contactBotMapper;
    private final ContactBindingService bindingService;

    public AdminContactController(
            ContactMapper contactMapper,
            ContactBotMapper contactBotMapper,
            ContactBindingService bindingService) {
        this.contactMapper = contactMapper;
        this.contactBotMapper = contactBotMapper;
        this.bindingService = bindingService;
    }

    @GetMapping
    public List<ContactView> list(@RequestParam(required = false) String botId) {
        List<Contact> contacts = contactMapper.selectList(
                new LambdaQueryWrapper<Contact>().orderByDesc(Contact::getUpdatedAt)
        );
        return contacts.stream()
                .map(c -> ContactView.from(c, bindingService.getLinkedBotIds(c.getId())))
                .filter(v -> botId == null || botId.isBlank() || v.getLinkedBotIds().contains(botId))
                .toList();
    }

    @PostMapping
    @Transactional
    public ContactView create(@Valid @RequestBody ContactSaveRequest request) {
        String code = trimOrNull(request.getCode());
        String lineId = request.getLineId().trim();
        ensureUniqueCode(code, null);
        ensureUniqueLineId(lineId, null);

        LocalDateTime now = LocalDateTime.now();
        Contact contact = new Contact();
        contact.setId(UUID.randomUUID().toString());
        contact.setCode(code);
        contact.setName(request.getName().trim());
        contact.setLineId(lineId);
        contact.setContactType(request.getContactType().trim());
        contact.setCreatedAt(now);
        contact.setUpdatedAt(now);
        contactMapper.insert(contact);

        if (request.getLinkedBotIds() != null) {
            bindingService.replaceBindings(contact.getId(), request.getLinkedBotIds());
        }
        return ContactView.from(contact, bindingService.getLinkedBotIds(contact.getId()));
    }

    @PutMapping("/{id}")
    @Transactional
    public ContactView update(@PathVariable String id, @Valid @RequestBody ContactSaveRequest request) {
        Contact existing = contactMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "找不到聯絡人: " + id);
        }

        String code = trimOrNull(request.getCode());
        ensureUniqueCode(code, id);

        existing.setCode(code);
        existing.setName(request.getName().trim());
        existing.setContactType(request.getContactType().trim());
        existing.setUpdatedAt(LocalDateTime.now());
        contactMapper.updateById(existing);

        if (request.getLinkedBotIds() != null) {
            bindingService.replaceBindings(id, request.getLinkedBotIds());
        }
        return ContactView.from(existing, bindingService.getLinkedBotIds(id));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable String id) {
        Contact existing = contactMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "找不到聯絡人: " + id);
        }
        contactBotMapper.delete(new LambdaQueryWrapper<ContactBot>().eq(ContactBot::getContactId, id));
        contactMapper.deleteById(id);
        return Map.of("success", true);
    }

    private void ensureUniqueCode(String code, String selfId) {
        if (code == null) {
            return;
        }
        LambdaQueryWrapper<Contact> wrapper = new LambdaQueryWrapper<Contact>().eq(Contact::getCode, code);
        if (selfId != null) {
            wrapper.ne(Contact::getId, selfId);
        }
        Long count = contactMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "聯絡人代碼已被使用: " + code);
        }
    }

    private void ensureUniqueLineId(String lineId, String selfId) {
        LambdaQueryWrapper<Contact> wrapper = new LambdaQueryWrapper<Contact>().eq(Contact::getLineId, lineId);
        if (selfId != null) {
            wrapper.ne(Contact::getId, selfId);
        }
        Long count = contactMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "此 LINE ID 已存在於通訊錄: " + lineId);
        }
    }

    private String trimOrNull(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        return val.trim();
    }
}
