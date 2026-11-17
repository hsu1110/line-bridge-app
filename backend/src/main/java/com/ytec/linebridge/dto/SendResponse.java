package com.ytec.linebridge.dto;

/**
 * 發送訊息回應 DTO
 */
public class SendResponse {

    private boolean success;
    private String jobId;
    private String status;
    private String renderedContent;
    private String error;

    public static SendResponse queued(String jobId, String renderedContent) {
        SendResponse resp = new SendResponse();
        resp.success = true;
        resp.jobId = jobId;
        resp.status = "QUEUED";
        resp.renderedContent = renderedContent;
        return resp;
    }

    public static SendResponse sent(String jobId, String renderedContent) {
        SendResponse resp = new SendResponse();
        resp.success = true;
        resp.jobId = jobId;
        resp.status = "SUCCESS";
        resp.renderedContent = renderedContent;
        return resp;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRenderedContent() {
        return renderedContent;
    }

    public void setRenderedContent(String renderedContent) {
        this.renderedContent = renderedContent;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
