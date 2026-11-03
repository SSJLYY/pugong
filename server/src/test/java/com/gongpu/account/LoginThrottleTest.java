package com.gongpu.account;

import com.gongpu.common.ApiException;
import com.gongpu.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 第六批第 62 轮：登录防爆破——连续失败 5 次锁定，锁定中正确密码也拒，成功清零。 */
class LoginThrottleTest {

    private AccountService svc;
    private User user;

    private AccountService newSvc() {
        UserRepository repo = mock(UserRepository.class);
        user = new User();
        user.setId(9L);
        user.setUsername("victim");
        user.setPasswordHash(new BCryptPasswordEncoder().encode("rightpass1"));
        when(repo.findByUsername("victim")).thenReturn(Optional.of(user));
        return new AccountService(repo, new JwtService("test-secret-test-secret-0123456789", 168));
    }

    @Test
    void locksAfterFiveFailures() {
        svc = newSvc();
        for (int i = 0; i < 5; i++) {
            ApiException e = assertThrows(ApiException.class, () -> svc.login("victim", "wrong"));
            assertEquals(401, e.getStatus());
        }
        assertTrue(svc.isLoginLocked("victim"), "5 次失败后应锁定");
        ApiException locked = assertThrows(ApiException.class, () -> svc.login("victim", "rightpass1"));
        assertEquals(429, locked.getStatus(), "锁定中即便密码正确也应 429");
    }

    @Test
    void successResetsCounter() {
        svc = newSvc();
        for (int i = 0; i < 4; i++) {
            assertThrows(ApiException.class, () -> svc.login("victim", "wrong"));
        }
        svc.login("victim", "rightpass1");
        for (int i = 0; i < 4; i++) {
            assertThrows(ApiException.class, () -> svc.login("victim", "wrong"));
        }
        org.junit.jupiter.api.Assertions.assertFalse(svc.isLoginLocked("victim"),
                "成功登录清零后 4 次失败不应锁定");
    }
}
