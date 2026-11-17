package com.ytec.linebridge.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 新增／編輯模板請求 DTO
 */
public class TemplateSaveRequest {

    @NotBlank(message = "模板代碼不可為空")
    private String code;

    @NotBlank(message = "模板名稱不可為空")
    private String name;

    @NotBlank(message = "模板內容不可為空")
    private String content;

    private String description;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
