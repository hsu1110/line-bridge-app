package com.ytec.linebridge.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理員登入請求 DTO
 */
public class LoginRequest {

    @NotBlank(message = "請輸入帳號")
    private String username;

    @NotBlank(message = "請輸入密碼")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
