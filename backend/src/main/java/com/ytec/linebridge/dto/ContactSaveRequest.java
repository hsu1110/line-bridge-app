package com.ytec.linebridge.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 新增／編輯聯絡人請求 DTO
 */
public class ContactSaveRequest {

    private String code;

    @NotBlank(message = "聯絡人名稱不可為空")
    private String name;

    @NotBlank(message = "LINE ID 不可為空")
    private String lineId;

    @NotBlank(message = "類型不可為空")
    private String contactType;

    private List<String> linkedBotIds;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
    }

    public String getContactType() {
        return contactType;
    }

    public void setContactType(String contactType) {
        this.contactType = contactType;
    }

    public List<String> getLinkedBotIds() {
        return linkedBotIds;
    }

    public void setLinkedBotIds(List<String> linkedBotIds) {
        this.linkedBotIds = linkedBotIds;
    }
}
