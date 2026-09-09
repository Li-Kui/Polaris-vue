package com.polaris.platform.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 中台独立 JWT 工具类。
 * 与管理后台 JWT 使用不同的 secret，互不干扰。
 */
@Component
public class PlatformJwtUtils implements InitializingBean {

    private static final int MIN_SECRET_BYTES = 64;

    @Value("${platform.jwt.secret:}")
    private String secret;

    @Value("${platform.jwt.expiration:86400000}")
    private long expiration;

    private byte[] signingKey;

    @Override
    public void afterPropertiesSet() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("中台JWT密钥未配置，请设置 PLATFORM_JWT_SECRET");
        }

        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("中台JWT密钥长度不能少于64字节");
        }
        signingKey = secretBytes;
    }

    public String generateToken(Long userId, Long tenantId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("tenantId", tenantId);
        claims.put("username", username);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS512, signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .setSigningKey(signingKey)
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    public Long getTenantId(String token) {
        return parseToken(token).get("tenantId", Long.class);
    }

    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }
}
