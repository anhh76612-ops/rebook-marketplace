package com.oldbook.service.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RevokedTokenService {

    /*
     * Lưu:
     * jti -> thời điểm hết hạn của token
     */
    private final Map<String, Instant> revokedTokens =
            new ConcurrentHashMap<>();

    /**
     * Đánh dấu token đã bị thu hồi.
     */
    public void revokeToken(String jti, Instant expiration) {
        cleanupExpiredTokens();

        revokedTokens.put(jti, expiration);
    }

    /**
     * Kiểm tra token có bị thu hồi hay chưa.
     */
    public boolean isRevoked(String jti) {
        cleanupExpiredTokens();

        return revokedTokens.containsKey(jti);
    }

    /**
     * Xóa các token đã hết hạn khỏi bộ nhớ.
     */
    private void cleanupExpiredTokens() {

        Instant now = Instant.now();

        revokedTokens.entrySet().removeIf(entry ->
                entry.getValue().isBefore(now)
        );
    }
}