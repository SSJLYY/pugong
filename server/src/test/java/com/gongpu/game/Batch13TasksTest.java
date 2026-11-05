package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十三批集群五：传承/扫荡任务模板、黑市扩池、九档里程碑。 */
class Batch13TasksTest {

    @Test
    void countersAndTemplates() {
        PlayerState s = NewState.create();
        s.stats.inherits = 7;
        s.stats.sweeps = 1234;
        assertEquals(0, new BigDecimal("7").compareTo(DailyQuests.counterOf(s, "inherit")));
        assertEquals(0, new BigDecimal("1234").compareTo(DailyQuests.counterOf(s, "sweep")));
        assertEquals(11, DailyQuests.TEMPLATES.size(), "每日模板 11");
        assertEquals(11, WeeklyQuests.TEMPLATES.size(), "周常模板 11");
        assertEquals(13, MonthlyQuests.TEMPLATES.size(), "月常模板 13");
        assertTrue(WeeklyQuests.templateOf("inherit") != null && WeeklyQuests.templateOf("sweep") != null);
        assertTrue(MonthlyQuests.templateOf("inherit") != null && MonthlyQuests.templateOf("sweep") != null);
        assertTrue(DailyQuests.TEMPLATES.stream().anyMatch(t -> t.id().equals("inherit")));
        assertTrue(DailyQuests.TEMPLATES.stream().anyMatch(t -> t.id().equals("sweep")));
    }

    @Test
    void blackMarketPoolValidAndGrown() {
        assertEquals(32, BlackMarket.POOL.size(), "黑市池 32");
        for (String id : BlackMarket.POOL) {
            assertTrue(GameConst.POTIONS.containsKey(id), "黑市池条目必须是可解析药剂：" + id);
        }
    }

    @Test
    void milestoneNineTiers() {
        assertEquals(9, GameService.ACH_MILESTONE_TIERS.length, "里程碑九档");
        assertEquals(135, GameService.ACH_MILESTONE_TIERS[8]);
        assertEquals(10_000_000, GameService.ACH_MILESTONE_BONUS[8]);
        // 结算：已领 135 项应发第九档 1000 万（位掩码第 8 位）
        PlayerState s = NewState.create();
        s.gold = BigDecimal.ZERO;
        for (int i = 0; i < 135; i++) {
            s.achClaimed.add("dummy_" + i);
        }
        long paid = GameService.settleAchMilestone(s);
        assertTrue(paid >= 10_000_000L, "九档全清应至少发 1000 万，实际 " + paid);
        assertEquals(0, GameService.settleAchMilestone(s), "重结算不得重发");
    }
}
