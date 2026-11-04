package com.gongpu.game;

import com.gongpu.web.TimingInterceptor;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 62 轮：advance 帧耗时指标——记录后帧数/慢帧/最大值口径正确。
 * 进程级静态计数，用增量断言避免与其他测试互相干扰。
 */
class FrameTimingTest {

    @Test
    void frameStatsTrackCounts() {
        Map<String, Object> before = TimingInterceptor.frameStats();
        long frames0 = ((Number) before.get("frames")).longValue();
        long slow0 = ((Number) before.get("slowFrames")).longValue();
        TimingInterceptor.recordFrame(10);
        TimingInterceptor.recordFrame(80);
        Map<String, Object> after = TimingInterceptor.frameStats();
        assertEquals(frames0 + 2, ((Number) after.get("frames")).longValue());
        assertEquals(slow0 + 1, ((Number) after.get("slowFrames")).longValue(), "80ms 应计一帧慢帧（阈值 50ms）");
        assertTrue(((Number) after.get("maxMs")).longValue() >= 80);
        assertTrue(((Number) after.get("avgMs")).doubleValue() > 0);
    }
}
