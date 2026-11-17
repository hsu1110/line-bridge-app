package com.ytec.linebridge.dto;

import com.ytec.linebridge.entity.Contact;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通訊錄列表與編輯視圖（包含已綁定的 Bot 清單）
 */
public class ContactView {

    private String id;
    private String code;
    private String name;
    private String lineId;
    private String contactType;
    private List<String> linkedBotIds;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ContactView from(Contact contact, List<String> linkedBotIds) {
        ContactView view = new ContactView();
        view.id = contact.getId();
        view.code = contact.getCode();
        view.name = contact.getName();
        view.lineId = contact.getLineId();
        view.contactType = contact.getContactType();
        view.linkedBotIds = linkedBotIds;
        view.createdAt = contact.getCreatedAt();
        view.updatedAt = contact.getUpdatedAt();
        return view;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getLineId() {
        return lineId;
    }

    public String getContactType() {
        return contactType;
    }

    public List<String> getLinkedBotIds() {
        return linkedBotIds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
