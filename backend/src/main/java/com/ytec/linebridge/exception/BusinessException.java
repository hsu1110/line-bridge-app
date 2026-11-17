package com.ytec.linebridge.exception;

import org.springframework.http.HttpStatus;

/**
 * 可預期業務例外（對應明確 HTTP 狀態碼與前端可讀訊息）
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public BusinessException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
