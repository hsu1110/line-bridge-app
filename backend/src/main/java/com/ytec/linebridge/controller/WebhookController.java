package com.ytec.linebridge.controller;

import com.ytec.linebridge.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LINE Webhook 接收端點（支援 /api/webhook/{botCode} 與 /api/webhook）
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/{botCode}")
    public ResponseEntity<String> receiveForBot(
            @PathVariable String botCode,
            @RequestHeader(value = "X-Line-Signature", required = false) String signature,
            @RequestBody byte[] rawBody) {
        webhookService.handleWebhook(botCode, rawBody, signature);
        return ResponseEntity.ok("OK");
    }

    @PostMapping
    public ResponseEntity<String> receiveByDestination(
            @RequestHeader(value = "X-Line-Signature", required = false) String signature,
            @RequestBody byte[] rawBody) {
        webhookService.handleWebhook(null, rawBody, signature);
        return ResponseEntity.ok("OK");
    }
}
