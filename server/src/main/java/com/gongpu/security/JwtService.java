package com.gongpu.security;

import com.gongpu.common.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** JWT 签发与校验。 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long ttlHours;

    public JwtService(@Value("${gongpu.jwt.secret}") String secret,
                      @Value("${gongpu.jwt.ttl-hours:168}") long ttlHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret 至少需要 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttlHours = ttlHours;
    }

    public String issue(long userId, String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("u", username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlHours * 3600)))
                .signWith(key)
                .compact();
    }

    /** 一次解析同时拿到 userId 与用户名（claim "u"，签发时写入）。 */
    public record Verified(long userId, String username) {
    }

    public Verified verify(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return new Verified(Long.parseLong(c.getSubject()), c.get("u", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("登录状态已失效，请重新登录");
        }
    }

    public long verifyAndGetUserId(String token) {
        return verify(token).userId();
    }

    public long ttlSeconds() {
        return ttlHours * 3600;
    }
}
