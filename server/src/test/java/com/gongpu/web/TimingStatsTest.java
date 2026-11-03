package com.gongpu.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第七批第 74 轮：TimingInterceptor 进程级指标——慢判定、计数与最大值，
 * /api/health 的 perf 段数据源。计数是静态全局，断言用增量口径防与其他用例互相污染。
 */
class TimingStatsTest {

    @Test
    void recordCountsSlowAndMax() {
        Map<String, Object> before = TimingInterceptor.stats(500);
        long total0 = ((Number) before.get("requests")).longValue();
        long slow0 = ((Number) before.get("slow")).longValue();
        long max0 = ((Number) before.get("maxMs")).longValue();

        assertFalse(TimingInterceptor.record(120, 500), "120ms 不算慢");
        assertTrue(TimingInterceptor.record(640, 500), "640ms 应判慢");
        assertFalse(TimingInterceptor.record(499, 500));

        Map<String, Object> after = TimingInterceptor.stats(500);
        assertEquals(total0 + 3, ((Number) after.get("requests")).longValue());
        assertEquals(slow0 + 1, ((Number) after.get("slow")).longValue());
        assertEquals(Math.max(max0, 640), ((Number) after.get("maxMs")).longValue());
        assertEquals(500, ((Number) after.get("slowMs")).longValue());
    }
}
