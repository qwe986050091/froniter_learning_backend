package org.example.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenService {

    private final Map<String, TokenInfo> tokenStore = new ConcurrentHashMap<>();

    private static final long TOKEN_EXPIRE_MS = 24 * 60 * 60 * 1000L; // 24 hours

    public String generateToken(String username) {
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        tokenStore.put(token, new TokenInfo(username, now + TOKEN_EXPIRE_MS));
        return token;
    }

    public String generateRefreshToken(String username) {
        String refreshToken = java.util.UUID.randomUUID().toString().replace("-", "");
        long now = System.currentTimeMillis();
        tokenStore.put(refreshToken, new TokenInfo(username, now + 7 * TOKEN_EXPIRE_MS));
        return refreshToken;
    }

    public String validateToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        TokenInfo info = tokenStore.get(token);
        if (info == null) {
            return null;
        }
        if (System.currentTimeMillis() > info.expireAt) {
            tokenStore.remove(token);
            return null;
        }
        return info.username;
    }

    public void invalidateToken(String token) {
        if (token != null) {
            tokenStore.remove(token);
        }
    }

    private record TokenInfo(String username, long expireAt) {}
}
