package com.gongpu.account;

import com.gongpu.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 75 轮：密码规则加固——BCrypt 72 字节截断守卫（注册/改密共用 checkPasswordRules）。
 */
class PasswordRulesTest {

    @Test
    void rejectsTooShortAndNull() {
        ApiException e = assertThrows(ApiException.class, () -> AccountService.checkPasswordRules("12345"));
        assertEquals(400, e.getStatus());
        assertThrows(ApiException.class, () -> AccountService.checkPasswordRules(null));
        assertThrows(ApiException.class, () -> AccountService.checkPasswordRules(""));
    }

    @Test
    void rejectsOver72BytesAscii() {
        String ok72 = "a".repeat(72);
        assertDoesNotThrow(() -> AccountService.checkPasswordRules(ok72), "恰 72 字节应放行");
        ApiException e = assertThrows(ApiException.class,
                () -> AccountService.checkPasswordRules("a".repeat(73)));
        assertTrue(e.getMessage().contains("72"), "超长应明确提示 72 字节上限");
    }

    @Test
    void rejectsOver72BytesMultibyte() {
        // 汉字 3 字节/字：24 字 = 72 字节放行，25 字 = 75 字节拒绝（按字节而非字数判定）
        assertDoesNotThrow(() -> AccountService.checkPasswordRules("汉".repeat(24)));
        assertThrows(ApiException.class, () -> AccountService.checkPasswordRules("汉".repeat(25)));
    }

    @Test
    void normalPasswordsPass() {
        assertDoesNotThrow(() -> AccountService.checkPasswordRules("testpass123"));
        assertDoesNotThrow(() -> AccountService.checkPasswordRules("密码abc123"));
    }
}
