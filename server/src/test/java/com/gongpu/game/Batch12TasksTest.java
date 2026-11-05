package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十二批集群四：觉醒/进化任务计数、周/月常新模板、黑市扩池、里程碑八档。 */
class Batch12TasksTest {

    @Test
    void awakenEvolveCounters() {
        PlayerState s = NewState.create();
        s.stats.awakens = 7;
        s.stats.petEvolves = 2;
        assertEquals(0, DailyQuests.counterOf(s, "awaken").compareTo(BigDecimal.valueOf(7)));
        assertEquals(0, DailyQuests.counterOf(s, "evolve").compareTo(BigDecimal.valueOf(2)));
        assertEquals(11, WeeklyQuests.TEMPLATES.size(), "周常模板 9");
        assertEquals(13, MonthlyQuests.TEMPLATES.size(), "月常模板 11");
        assertTrue(WeeklyQuests.templateOf("awaken") != null && WeeklyQuests.templateOf("evolve") != null);
        assertTrue(MonthlyQuests.templateOf("awaken") != null && MonthlyQuests.templateOf("evolve") != null);
    }

    @Test
    void blackMarketPool28AllResolvable() {
        new com.gongpu.cfg.GameData(null, null, null, null, null, null, null);
        assertEquals(32, BlackMarket.POOL.size(), "黑市池 28");
        for (String id : BlackMarket.POOL) {
            assertTrue(GameConst.POTIONS.containsKey(id), "池条目必须是 POTIONS 可解析 id：" + id);
        }
    }

    @Test
    void milestoneEightTiers() {
        assertEquals(9, GameService.ACH_MILESTONE_TIERS.length);
        assertEquals(120, GameService.ACH_MILESTONE_TIERS[7]);
        assertEquals(5_000_000, GameService.ACH_MILESTONE_BONUS[7]);
        // 已领满 120 个成就时第八档应发奖（用掩码直查结算）
        PlayerState s = NewState.create();
        for (int i = 0; i < 120; i++) {
            s.achClaimed.add("ach_" + i);
        }
        BigDecimal before = s.gold;
        GameService.settleAchMilestone(s);
        assertTrue(s.gold.compareTo(before) > 0, "里程碑结算应发放奖金（含第八档）");
        assertTrue((s.stats.achMilestonePaid & (1 << 7)) != 0, "第八档掩码应置位");
    }

    @Test
    void evolveAwakenAchievements() {
        PlayerState s = NewState.create();
        s.stats.petEvolves = 5;
        s.stats.awakens = 25;
        var got = Achievements.evaluate(s);
        assertTrue(got.contains("evolve_5"));
        assertTrue(got.contains("awaken_25"));
    }
}
