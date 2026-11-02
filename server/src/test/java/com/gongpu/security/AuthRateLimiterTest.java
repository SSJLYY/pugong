package com.gongpu.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * 认证限速器容错测试:Redis 不可用(null 模板)时必须放行,不阻断登录/注册可用性。
 * 真实限速行为由本地/服务器冒烟与 GUI 验证覆盖(依赖运行环境 Redis)。
 */
class AuthRateLimiterTest {

    @Test
    void passesThroughWhenRedisUnavailable() {
        AuthRateLimiter limiter = new AuthRateLimiter(null);
        assertDoesNotThrow(() -> limiter.checkLoginAllowed("203.0.113.7"),
                "Redis 不可用时登录检查应放行");
        assertDoesNotThrow(() -> limiter.onLoginFail("203.0.113.7"),
                "Redis 不可用时失败记账应静默");
        assertDoesNotThrow(() -> limiter.checkRegisterAllowed("203.0.113.7"),
                "Redis 不可用时注册检查应放行");
        assertDoesNotThrow(() -> limiter.onRegister("203.0.113.7"));
        assertDoesNotThrow(() -> limiter.onLoginSuccess("203.0.113.7"));
    }

    @Test
    void loopbackAlwaysAllowed() {
        AuthRateLimiter limiter = new AuthRateLimiter(null);
        assertDoesNotThrow(() -> limiter.checkLoginAllowed("127.0.0.1"),
                "回环地址(冒烟/本地调试)不限速");
        assertDoesNotThrow(() -> limiter.checkRegisterAllowed("0:0:0:0:0:0:0:1"));
    }
}
