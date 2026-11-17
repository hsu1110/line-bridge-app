package com.ytec.linebridge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ytec.linebridge.entity.Contact;
import com.ytec.linebridge.entity.ContactBot;
import com.ytec.linebridge.mapper.ContactBotMapper;
import com.ytec.linebridge.mapper.ContactMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 聯絡人與 Bot 綁定關係管理服務
 */
@Service
public class ContactBindingService {

    private static final String CODE_ALPHABET = "0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int SHORT_CODE_LENGTH = 6;
    private static final int MAX_NAME_UTF8_BYTES = 180;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ContactMapper contactMapper;
    private final ContactBotMapper contactBotMapper;

    public ContactBindingService(ContactMapper contactMapper, ContactBotMapper contactBotMapper) {
        this.contactMapper = contactMapper;
        this.contactBotMapper = contactBotMapper;
    }

    /**
     * 新增或更新聯絡人並綁定至指定 Bot（首次建立時自動產生純英數安全短碼）
     */
    @Transactional
    public Contact upsertAndBind(String lineId, String name, String contactType, String botId) {
        LocalDateTime now = LocalDateTime.now();
        String safeName = truncateUtf8Bytes(name != null ? name.trim() : "Unknown", MAX_NAME_UTF8_BYTES);
        Contact contact = contactMapper.selectOne(
                new LambdaQueryWrapper<Contact>().eq(Contact::getLineId, lineId)
        );

        if (contact == null) {
            contact = new Contact();
            contact.setId(UUID.randomUUID().toString());
            contact.setLineId(lineId);
            contact.setName(safeName);
            contact.setContactType(contactType);
            // 產生純英數短碼（例如 USR_A3F9K2 / GRP_B8C1D4），避免暱稱改名、撞名或 Oracle 欄位超長問題
            contact.setCode(generateUniqueShortCode(contactType));
            contact.setCreatedAt(now);
            contact.setUpdatedAt(now);
            contactMapper.insert(contact);
        } else {
            contact.setName(safeName);
            contact.setContactType(contactType);
            if (contact.getCode() == null || contact.getCode().isBlank()) {
                contact.setCode(generateUniqueShortCode(contactType));
            }
            contact.setUpdatedAt(now);
            contactMapper.updateById(contact);
        }

        ensureBinding(contact.getId(), botId);
        return contact;
    }

    /**
     * 確保聯絡人與特定 Bot 的關聯存在
     */
    public void ensureBinding(String contactId, String botId) {
        if (contactId == null || botId == null) {
            return;
        }
        Long count = contactBotMapper.selectCount(
                new LambdaQueryWrapper<ContactBot>()
                        .eq(ContactBot::getContactId, contactId)
                        .eq(ContactBot::getBotId, botId)
        );
        if (count == null || count == 0) {
            ContactBot cb = new ContactBot();
            cb.setId(UUID.randomUUID().toString());
            cb.setContactId(contactId);
            cb.setBotId(botId);
            cb.setBoundAt(LocalDateTime.now());
            contactBotMapper.insert(cb);
        }
    }

    /**
     * 僅解除特定 Bot 的綁定（封鎖或退群時不影響其他 Bot）
     */
    @Transactional
    public void unbindByLineId(String lineId, String botId) {
        Contact contact = contactMapper.selectOne(
                new LambdaQueryWrapper<Contact>().eq(Contact::getLineId, lineId)
        );
        if (contact == null) {
            return;
        }
        contactBotMapper.delete(
                new LambdaQueryWrapper<ContactBot>()
                        .eq(ContactBot::getContactId, contact.getId())
                        .eq(ContactBot::getBotId, botId)
        );
    }

    /**
     * 取代某聯絡人的所有 Bot 綁定清單（供管理後台手動調整使用）
     */
    @Transactional
    public void replaceBindings(String contactId, List<String> botIds) {
        contactBotMapper.delete(
                new LambdaQueryWrapper<ContactBot>().eq(ContactBot::getContactId, contactId)
        );
        if (botIds == null || botIds.isEmpty()) {
            return;
        }
        for (String botId : botIds) {
            if (botId != null && !botId.isBlank()) {
                ensureBinding(contactId, botId);
            }
        }
    }

    public boolean isBound(String contactId, String botId) {
        Long count = contactBotMapper.selectCount(
                new LambdaQueryWrapper<ContactBot>()
                        .eq(ContactBot::getContactId, contactId)
                        .eq(ContactBot::getBotId, botId)
        );
        return count != null && count > 0;
    }

    public List<String> getLinkedBotIds(String contactId) {
        return contactBotMapper.selectList(
                new LambdaQueryWrapper<ContactBot>().eq(ContactBot::getContactId, contactId)
        ).stream().map(ContactBot::getBotId).toList();
    }

    private String generateUniqueShortCode(String contactType) {
        String prefix = "group".equalsIgnoreCase(contactType) ? "GRP_" : "USR_";
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < SHORT_CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET.charAt(SECURE_RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            String candidate = sb.toString();
            Long count = contactMapper.selectCount(
                    new LambdaQueryWrapper<Contact>().eq(Contact::getCode, candidate)
            );
            if (count == null || count == 0) {
                return candidate;
            }
        }
        // 極端碰撞時的後備方案：使用 UUID 前 12 碼
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String truncateUtf8Bytes(String input, int maxBytes) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return input;
        }
        StringBuilder sb = new StringBuilder();
        int currentBytes = 0;
        for (int i = 0; i < input.length(); ) {
            int codePoint = input.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            int chBytes = ch.getBytes(StandardCharsets.UTF_8).length;
            if (currentBytes + chBytes > maxBytes) {
                break;
            }
            sb.append(ch);
            currentBytes += chBytes;
            i += Character.charCount(codePoint);
        }
        return sb.toString();
    }
}
