package com.gongpu.account;

import com.gongpu.common.ApiException;
import com.gongpu.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 第三批第 4 轮：修改密码——原密码校验、新密码规则（≥6 位、不得与原密码相同）、换哈希后旧密码失效。
 */
class ChangePasswordTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final User user = new User();
    private final AccountService service;

    ChangePasswordTest() {
        user.setId(7L);
        user.setUsername("tester");
        user.setPasswordHash(encoder.encode("oldpass123"));
        UserRepository repo = mock(UserRepository.class);
        when(repo.findById(7L)).thenReturn(Optional.of(user));
        service = new AccountService(repo, mock(JwtService.class));
    }

    @Test
    void changeSucceedsAndOldPasswordDies() {
        service.changePassword(7L, "oldpass123", "newpass456");
        assertTrue(encoder.matches("newpass456", user.getPasswordHash()), "新密码应生效");
        assertTrue(!encoder.matches("oldpass123", user.getPasswordHash()), "旧密码应失效");
    }

    @Test
    void wrongOldPasswordRejected() {
        assertThrows(ApiException.class, () -> service.changePassword(7L, "nope999", "newpass456"));
        assertTrue(encoder.matches("oldpass123", user.getPasswordHash()), "失败不应改动哈希");
    }

    @Test
    void shortOrSameNewPasswordRejected() {
        assertThrows(ApiException.class, () -> service.changePassword(7L, "oldpass123", "abc"));
        assertThrows(ApiException.class, () -> service.changePassword(7L, "oldpass123", "oldpass123"));
    }
}
