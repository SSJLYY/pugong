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

    /** 第二十批·轮6：Verified 版续期（拦截器热路径单解析）——行为与 String 版一致。 */
    @Test
    void verifiedOverloadRenewMatchesStringOverload() {
        JwtService jwt = new JwtService(SECRET, 1);
        String token = jwt.issue(42, "steven", 3);
        JwtService.Verified v = jwt.verify(token);
        assertTrue(v.expiresAtEpochSec() > 0, "Verified 必须带过期时间");
        var renewed = jwt.renewIfExpiringSoon(v);
        assertTrue(renewed.isPresent(), "Verified 版：剩余 1 小时应续期");
        JwtService.Verified nv = jwt.verify(renewed.get());
        assertEquals(42, nv.userId());
        assertEquals(3, nv.tokenVersion(), "续期令牌应保留版本号");
    }

    @Test
    void verifiedOverloadKeepsLongLivedToken() {
        JwtService jwt = new JwtService(SECRET, 168);
        JwtService.Verified v = jwt.verify(jwt.issue(7, "steven"));
        assertTrue(jwt.renewIfExpiringSoon(v).isEmpty(), "剩余 7 天不应续期");
    }
}
