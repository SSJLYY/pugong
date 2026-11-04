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

    /** 第九批第 68 轮：接线锁——限流检查须在 run 的 load 之前执行，且本批新端点都带 action 名走 run（自动纳入限流）。 */
    @Test
    void rateCheckWiredBeforeLoadAndBatch9EndpointsCarryActions() throws Exception {
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/gongpu/web/GameController.java"));
        int runStart = src.indexOf("Map<String, Object> run(");
        int rate = src.indexOf("checkWriteRate(uid", runStart);
        int load = src.indexOf("saves.load", runStart);
        org.junit.jupiter.api.Assertions.assertTrue(rate > runStart, "run 内须调用 checkWriteRate");
        org.junit.jupiter.api.Assertions.assertTrue(load > rate, "限流检查必须在 load 之前（省掉被限流请求的读盘）");
        for (String action : new String[]{"\"adopt-pet\"", "\"feed-pet-batch\"", "\"monthly-claim\"",
                "\"sell-all-materials\"", "\"fuse-all-gems\"", "\"unsocket-all\"",
                "\"sell-extra-blueprints\"", "\"mat-exchange-all\"", "\"rename-pet\""}) {
            org.junit.jupiter.api.Assertions.assertTrue(src.contains(action),
                    "端点须带 action 名走 run 以纳入写限流: " + action);
        }
    }
}
