package com.gongpu.account;

import com.gongpu.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 第六批第 61 轮：改密后旧令牌失效——令牌版本随 JWT 携带，改密自增。 */
class TokenVersionTest {

    private final JwtService jwt = new JwtService("test-secret-test-secret-0123456789", 168);

    @Test
    void versionRoundTrips() {
        assertEquals(3, jwt.verify(jwt.issue(7, "steven", 3)).tokenVersion());
        assertEquals(0, jwt.verify(jwt.issue(7, "steven")).tokenVersion(), "无版本签发应为 0（兼容老令牌）");
    }

    @Test
    void changePasswordBumpsVersionAndNewTokensCarryIt() {
        UserRepository repo = mock(UserRepository.class);
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        User u = new User();
        u.setId(7L);
        u.setUsername("steven");
        u.setPasswordHash(enc.encode("oldpass123"));
        u.setTokenVersion(0);
        when(repo.findById(7L)).thenReturn(Optional.of(u));
        when(repo.findByUsername("steven")).thenReturn(Optional.of(u));
        AccountService svc = new AccountService(repo, jwt);

        String before = (String) svc.login("steven", "oldpass123").get("token");
        assertEquals(0, jwt.verify(before).tokenVersion());

        svc.changePassword(7L, "oldpass123", "newpass123");
        assertEquals(1, u.getTokenVersion(), "改密应自增令牌版本");

        Map<String, Object> after = svc.login("steven", "newpass123");
        assertEquals(1, jwt.verify((String) after.get("token")).tokenVersion(),
                "新令牌应携带新版本（旧令牌版本 0 将被拦截器判失效）");
        assertEquals(1, svc.currentTokenVersion(7L));
    }
}
