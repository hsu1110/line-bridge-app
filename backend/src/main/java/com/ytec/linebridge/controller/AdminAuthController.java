package com.ytec.linebridge.controller;

import com.ytec.linebridge.config.LineBridgeProperties;
import com.ytec.linebridge.dto.LoginRequest;
import com.ytec.linebridge.exception.BusinessException;
import com.ytec.linebridge.security.AdminAuthInterceptor;
import com.ytec.linebridge.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理後台登入與目前登入身分查詢
 */
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final LineBridgeProperties properties;
    private final JwtService jwtService;

    public AdminAuthController(LineBridgeProperties properties, JwtService jwtService) {
        this.properties = properties;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        String expectedUser = properties.getAdmin().getUsername();
        String expectedPass = properties.getAdmin().getPassword();

        if (!expectedUser.equals(request.getUsername()) || !expectedPass.equals(request.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤");
        }

        String token = jwtService.generateToken(expectedUser);
        return Map.of(
                "token", token,
                "username", expectedUser,
                "expiresInHours", properties.getAdmin().getJwtExpireHours()
        );
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        Object username = request.getAttribute(AdminAuthInterceptor.ATTR_ADMIN_USER);
        return Map.of("username", username != null ? username.toString() : "admin");
    }
}
