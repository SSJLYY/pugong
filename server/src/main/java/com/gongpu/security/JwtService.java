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

    /** 滑动续期阈值：剩余有效期不足 24 小时即换新。 */
    public static final long RENEW_THRESHOLD_SECONDS = 24 * 3600;

    /**
     * 滑动续期：令牌剩余有效期不足阈值时签发一枚新令牌，否则返回空。
     * 挂机玩家会话以周计，中途被 7 天 TTL 踢下线很伤体验；轮询请求天然构成活跃心跳，
     * 活跃即续期、真正闲置满 TTL 才过期。解析失败（已失效）返回空，鉴权该拒仍拒。
     */
    public java.util.Optional<String> renewIfExpiringSoon(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            long left = c.getExpiration().getTime() / 1000 - Instant.now().getEpochSecond();
            if (left < RENEW_THRESHOLD_SECONDS) {
                return java.util.Optional.of(
                        issue(Long.parseLong(c.getSubject()), c.get("u", String.class)));
            }
            return java.util.Optional.empty();
        } catch (JwtException | IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    public long ttlSeconds() {
        return ttlHours * 3600;
    }
}
