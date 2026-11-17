package com.ytec.linebridge.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 服務健康檢查端點
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${server.port:3000}")
    private int serverPort;

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
                "status", "running",
                "port", serverPort,
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
