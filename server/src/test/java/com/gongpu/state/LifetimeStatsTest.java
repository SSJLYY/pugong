package com.gongpu.state;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 第三批第 1 轮：运营统计终身口径——lifetimeKills 与 kills 同步累加；
 * 旧档解码以当世 kills 为终身种子；已有终身值不被小当世值回退。
 */
class LifetimeStatsTest {

    @Test
    void legacySaveSeedsLifetimeKillsFromCurrentLife() {
        PlayerState s = StateCodec.decode("{\"gen\":3,\"stats\":{\"kills\":50,\"punches\":999}}");
        assertEquals(0, s.stats.lifetimeKills.compareTo(new BigDecimal("50")),
                "旧档终身击杀应以当世击杀 50 为种子");
    }

    @Test
    void existingLifetimeKillsNotRegressedBySmallerCurrentLife() {
        PlayerState s = StateCodec.decode("{\"gen\":3,\"stats\":{\"kills\":5,\"lifetimeKills\":80}}");
        assertEquals(0, s.stats.lifetimeKills.compareTo(new BigDecimal("80")));
    }
}
