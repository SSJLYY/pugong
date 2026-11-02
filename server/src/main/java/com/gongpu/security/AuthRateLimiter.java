package com.gongpu.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 认证端点防爆破限速（Redis 计数）。
 * <p>登录失败按 IP 限速（10 分钟 10 次），注册按 IP 限速（1 小时 5 次，防批量刷号）。
 * Redis 不可用时放行（不阻断可用性，与 AdminGuard 同策略）。</p>
 */
@Component
public class AuthRateLimiter {

    private static final int LOGIN_FAIL_LIMIT = 10;
    private static final Duration LOGIN_FAIL_WINDOW = Duration.ofMinutes(10);
    private static final int REGISTER_LIMIT = 5;
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);

    private final StringRedisTemplate redis;

    public AuthRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 登录前检查：该 IP 是否已被失败次数锁死。 */
    public void checkLoginAllowed(String ip) {
        if (isLoopback(ip)) {
            return;   // 本机回环(冒烟/健康检查/本地调试)不限速
        }
        Long fails = counter("gongpu:auth:loginfail:" + ip);
        if (fails != null && fails >= LOGIN_FAIL_LIMIT) {
            throw com.gongpu.common.ApiException.forbidden(
                    "失败次数过多，账号登录已临时锁定，请 " + LOGIN_FAIL_WINDOW.toMinutes() + " 分钟后再试");
        }
    }

    /** 登录失败记账。 */
    public void onLoginFail(String ip) {
        if (isLoopback(ip)) {
            return;
        }
        increment("gongpu:auth:loginfail:" + ip, LOGIN_FAIL_WINDOW);
    }

    /** 登录成功清零（正常玩家不受历史失败影响）。 */
    public void onLoginSuccess(String ip) {
        try {
            redis.delete("gongpu:auth:loginfail:" + ip);
        } catch (RuntimeException ignored) {
            // Redis 不可用
        }
    }

    /** 注册前检查：该 IP 本小时是否还能注册。 */
    public void checkRegisterAllowed(String ip) {
        if (isLoopback(ip)) {
            return;
        }
        Long n = counter("gongpu:auth:reg:" + ip);
        if (n != null && n >= REGISTER_LIMIT) {
            throw com.gongpu.common.ApiException.forbidden(
                    "注册过于频繁，请 1 小时后再试");
        }
    }

    /** 注册记账（成功才记）。 */
    public void onRegister(String ip) {
        if (isLoopback(ip)) {
            return;
        }
        increment("gongpu:auth:reg:" + ip, REGISTER_WINDOW);
    }

    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    private Long counter(String key) {
        try {
            String v = redis.opsForValue().get(key);
            return v == null ? 0L : Long.parseLong(v);
        } catch (RuntimeException e) {
            return null;   // Redis 不可用 → 放行
        }
    }

    private void increment(String key, Duration window) {
        try {
            Long n = redis.opsForValue().increment(key);
            if (n != null && n == 1L) {
                redis.expire(key, window);
            }
        } catch (RuntimeException ignored) {
            // Redis 不可用 → 放行
        }
    }
}
