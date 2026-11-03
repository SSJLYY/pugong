package com.gongpu.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第三批第 5 轮：JWT 滑动续期——剩余 <24h 换新、剩余充足不换、坏令牌不换。
 */
class JwtRenewTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-32b";

    @Test
    void longLivedTokenNotRenewed() {
        JwtService jwt = new JwtService(SECRET, 168);
        String token = jwt.issue(42, "steven");
        assertTrue(jwt.renewIfExpiringSoon(token).isEmpty(), "剩余 7 天不应续期");
    }

    @Test
    void shortLivedTokenRenewedAndVerifies() {
        JwtService jwt = new JwtService(SECRET, 1);
        String token = jwt.issue(42, "steven");
        var renewed = jwt.renewIfExpiringSoon(token);
        assertTrue(renewed.isPresent(), "剩余 1 小时应续期");
        JwtService.Verified v = jwt.verify(renewed.get());
        assertEquals(42, v.userId());
        assertEquals("steven", v.username(), "续期令牌应保留用户名 claim");
    }

    @Test
    void garbageTokenNotRenewed() {
        JwtService jwt = new JwtService(SECRET, 1);
        assertTrue(jwt.renewIfExpiringSoon("not-a-token").isEmpty());
    }
}
