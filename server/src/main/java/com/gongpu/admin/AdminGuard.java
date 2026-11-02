package com.gongpu.admin;

import com.gongpu.common.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * 管理员鉴权：独立于玩家 JWT 体系。
 * <p>管理 token 的 subject 固定为 "admin"（不走数字 userId 的 verifyAndGetUserId），
 * 密码来自 application.yml 的 gongpu.admin.password；未配置时一律拒绝。</p>
 */
@Component
public class AdminGuard {

    public static final String SUBJECT = "admin";
    public static final long TTL_SECONDS = 7200;
    /** 登录失败限速:同 IP 10 分钟内最多 5 次失败。 */
    private static final int FAIL_LIMIT = 5;
    private static final long FAIL_WINDOW_MS = 600_000L;

    private final SecretKey key;
    private final String adminPassword;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;

    public AdminGuard(@Value("${gongpu.jwt.secret}") String secret,
                      @Value("${gongpu.admin.password:}") String adminPassword,
                      org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.adminPassword = adminPassword == null ? "" : adminPassword;
        this.redis = redis;
    }

    /** 登录：返回管理 token。clientIp 用于失败限速。 */
    public String login(String password, String clientIp) {
        if (adminPassword.isBlank()) {
            throw ApiException.forbidden("未配置管理密码（gongpu.admin.password）");
        }
        // 失败限速（Redis 计数;Redis 挂了退化为不限速,不阻断管理端可用性）
        String failKey = "gongpu:admin:fail:" + (clientIp == null ? "?" : clientIp);
        try {
            String fails = redis.opsForValue().get(failKey);
            if (fails != null && Long.parseLong(fails) >= FAIL_LIMIT) {
                throw ApiException.forbidden("失败次数过多，已临时锁定，请 10 分钟后再试");
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException ignored) {
            // Redis 不可用
        }
        // 常量时间比较,防时序侧信道逐字节猜密码
        byte[] expect = adminPassword.getBytes(StandardCharsets.UTF_8);
        byte[] actual = (password == null ? "" : password).getBytes(StandardCharsets.UTF_8);
        if (!java.security.MessageDigest.isEqual(expect, actual)) {
            try {
                Long n = redis.opsForValue().increment(failKey);
                if (n != null && n == 1L) {
                    redis.expire(failKey, java.time.Duration.ofMillis(FAIL_WINDOW_MS));
                }
            } catch (RuntimeException ignored) {
                // Redis 不可用
            }
            throw ApiException.forbidden("管理密码错误");
        }
        try {
            redis.delete(failKey);
        } catch (RuntimeException ignored) {
            // Redis 不可用
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(SUBJECT)
                .claim("role", "admin")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(TTL_SECONDS)))
                .signWith(key)
                .compact();
    }

    /** 校验请求头里的管理 token；失败抛 401。 */
    public void requireAdmin(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null || !h.startsWith("Bearer ")) {
            throw ApiException.unauthorized("管理员登录已失效");
        }
        String token = h.substring(7).trim();
        try {
            Claims c = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            if (!SUBJECT.equals(c.getSubject())) {
                throw ApiException.unauthorized("管理员登录已失效");
            }
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("管理员登录已失效");
        }
    }

    public long ttlSeconds() {
        return TTL_SECONDS;
    }
}
