package com.ytec.linebridge.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ytec.linebridge.config.LineBridgeProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * 輕量級 HS256 JWT 工具（無額外第三方相依）
 */
@Service
public class JwtService {

    private static final String HMAC_ALG = "HmacSHA256";
    private final LineBridgeProperties properties;
    private final ObjectMapper objectMapper;

    public JwtService(LineBridgeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 簽發管理員登入 Token
     */
    public String generateToken(String username) {
        try {
            long nowSec = Instant.now().getEpochSecond();
            long expSec = nowSec + (long) properties.getAdmin().getJwtExpireHours() * 3600L;

            String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                    "sub", username,
                    "iat", nowSec,
                    "exp", expSec
            ));

            String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
            String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = sign(signingInput);
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("產生 JWT 失敗", e);
        }
    }

    /**
     * 驗證 Token 並回傳管理員帳號名稱，失敗回傳 null
     */
    public String verifyAndGetSubject(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            String expectedSig = sign(signingInput);
            if (!MessageDigest.isEqual(
                    expectedSig.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8))) {
                return null;
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = objectMapper.readValue(payloadBytes, new TypeReference<>() {});
            Object expObj = claims.get("exp");
            if (!(expObj instanceof Number expNum)) {
                return null;
            }
            if (Instant.now().getEpochSecond() >= expNum.longValue()) {
                return null;
            }
            Object sub = claims.get("sub");
            return sub != null ? sub.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALG);
        byte[] secretBytes = properties.getAdmin().getJwtSecret().getBytes(StandardCharsets.UTF_8);
        mac.init(new SecretKeySpec(secretBytes, HMAC_ALG));
        return base64UrlEncode(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
