package com.gongpu.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第六批第 77 轮：滑动续期与令牌版本联动——续期新令牌继承原版本，不绕过改密失效。 */
class JwtRenewVersionTest {

    @Test
    void renewalCarriesTokenVersion() {
        // TTL 1 小时 < 24 小时续期阈值 → 必触发续期
        JwtService jwt = new JwtService("test-secret-test-secret-0123456789", 1);
        String old = jwt.issue(7, "steven", 5);
        Optional<String> renewed = jwt.renewIfExpiringSoon(old);
        assertTrue(renewed.isPresent(), "临期令牌应续期");
        JwtService.Verified v = jwt.verify(renewed.get());
        assertEquals(5, v.tokenVersion(), "续期令牌必须继承版本（否则改密失效被续期绕过）");
        assertEquals(7, v.userId());
        assertEquals("steven", v.username());
    }

    @Test
    void freshLongTokenNotRenewed() {
        JwtService jwt = new JwtService("test-secret-test-secret-0123456789", 168);
        assertTrue(jwt.renewIfExpiringSoon(jwt.issue(7, "steven", 2)).isEmpty(),
                "剩余有效期充足时不应续期");
    }
}
