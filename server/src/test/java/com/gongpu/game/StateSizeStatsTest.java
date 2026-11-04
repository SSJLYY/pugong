package com.gongpu.game;

import com.gongpu.state.SaveService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第八批第 64 轮：存档体积指标形状——health 的 save 段四键齐全、数值非负。
 * recordStateBytes 是包内方法，这里只验公开快照的形状契约。
 */
class StateSizeStatsTest {

    @Test
    void statsShapeComplete() {
        Map<String, Object> m = SaveService.stateSizeStats();
        for (String k : new String[]{"lastBytes", "avgBytes", "maxBytes", "persists"}) {
            assertTrue(m.containsKey(k), "save 段缺键 " + k);
            assertTrue(((Number) m.get(k)).longValue() >= 0, k + " 不应为负");
        }
    }
}
