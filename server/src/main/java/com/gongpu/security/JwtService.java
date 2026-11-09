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
        return issue(userId, username, 0);
    }

    /** 签发带令牌版本的令牌（claim "v"）：改密后版本自增，旧令牌被拦截器判失效。 */
    public String issue(long userId, String username, int tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("u", username)
                .claim("v", tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlHours * 3600)))
                .signWith(key)
                .compact();
    }

    /** 一次解析同时拿到 userId、用户名、令牌版本与过期时间（claim "u"/"v"；老令牌无 v 时为 0）。
     *  第二十批·轮6：expiresAtEpochSec 供滑动续期直接读，免去每请求的第二次 HMAC 验签+解析。 */
    public record Verified(long userId, String username, int tokenVersion, long expiresAtEpochSec) {
    }

    public Verified verify(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            Integer v = c.get("v", Integer.class);
            Date exp = c.getExpiration();
            return new Verified(Long.parseLong(c.getSubject()), c.get("u", String.class),
                    v == null ? 0 : v, exp == null ? 0 : exp.getTime() / 1000);
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
    /**
     * 滑动续期（热路径版）：直接消费 verify() 已解析的结果，不再二次验签。
     * 与 String 版语义一致——两次解析间隔仅微秒级，剩余有效期判定无差异。
     */
    public java.util.Optional<String> renewIfExpiringSoon(Verified v) {
        if (v == null || v.expiresAtEpochSec() <= 0) {
            return java.util.Optional.empty();
        }
        long left = v.expiresAtEpochSec() - Instant.now().getEpochSecond();
        if (left < RENEW_THRESHOLD_SECONDS) {
            return java.util.Optional.of(issue(v.userId(), v.username(), v.tokenVersion()));
        }
        return java.util.Optional.empty();
    }

    /** 兼容入口（测试/独立调用）：先验签再续期。 */
    public java.util.Optional<String> renewIfExpiringSoon(String token) {
        try {
            return renewIfExpiringSoon(verify(token));
        } catch (ApiException e) {
            // 解析失败（已失效）返回空，鉴权该拒仍拒
            return java.util.Optional.empty();
        }
    }

    public long ttlSeconds() {
        return ttlHours * 3600;
    }
}
