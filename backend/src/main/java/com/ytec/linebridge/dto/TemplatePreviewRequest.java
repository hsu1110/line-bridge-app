package com.ytec.linebridge.dto;

import java.util.Map;

/**
 * 模板即時渲染試算請求 DTO
 */
public class TemplatePreviewRequest {

    private String content;
    private Map<String, Object> params;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }
}
