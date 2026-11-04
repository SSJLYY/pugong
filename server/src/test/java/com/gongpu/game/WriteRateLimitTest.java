package com.gongpu.game;

import com.gongpu.common.ApiException;
import com.gongpu.web.GameController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 第八批第 67 轮：写端点限流——窗口内前 60 次放行、第 61 次 429；窗口滑出后恢复。
 * 用独立 uid 避免污染其他用例的计数。
 */
class WriteRateLimitTest {

    @Test
    void sixtiethWritePassesSixtyFirstThrottled() {
        long uid = 987_654_321L;
        long now = System.currentTimeMillis();
        for (int i = 0; i < 60; i++) {
            assertDoesNotThrow(() -> GameController.checkWriteRate(uid, now));
        }
        ApiException ex = assertThrows(ApiException.class,
                () -> GameController.checkWriteRate(uid, now), "第 61 次写应被限流");
        assertEquals(429, ex.getStatus());
        // 11 秒后窗口滑出，恢复放行
        assertDoesNotThrow(() -> GameController.checkWriteRate(uid, now + 11_000L));
        // 其他账号互不影响
        assertDoesNotThrow(() -> GameController.checkWriteRate(123_456_789L, now));
    }
}
