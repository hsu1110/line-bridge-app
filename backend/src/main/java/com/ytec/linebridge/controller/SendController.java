package com.ytec.linebridge.controller;

import com.ytec.linebridge.dto.SendRequest;
import com.ytec.linebridge.dto.SendResponse;
import com.ytec.linebridge.entity.ApiClient;
import com.ytec.linebridge.entity.SendLog;
import com.ytec.linebridge.security.ApiClientInterceptor;
import com.ytec.linebridge.service.MessageDispatchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供外部系統呼叫的訊息發送 API
 */
@RestController
@RequestMapping("/api/send")
public class SendController {

    private final MessageDispatchService dispatchService;

    public SendController(MessageDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping
    public ResponseEntity<SendResponse> send(@RequestBody SendRequest request, HttpServletRequest httpRequest) {
        ApiClient client = (ApiClient) httpRequest.getAttribute(ApiClientInterceptor.ATTR_API_CLIENT);
        String sourceIp = resolveClientIp(httpRequest);
        SendResponse response = dispatchService.dispatch(request, client, sourceIp, false);
        HttpStatus status = "QUEUED".equals(response.getStatus()) ? HttpStatus.ACCEPTED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/jobs/{jobId}")
    public SendLog getJob(@PathVariable String jobId) {
        return dispatchService.getJob(jobId);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
