package com.gongpu.game;

import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第十一批集群四：周常/月常模板扩充、黑市池 24、里程碑 105 档、新成就。 */
class Batch11TasksTest {

    @Test
    void templateCounts() {
        assertEquals(7, WeeklyQuests.TEMPLATES.size(), "周常模板 7");
        assertEquals(9, MonthlyQuests.TEMPLATES.size(), "月常模板 9");
        assertEquals(24, BlackMarket.POOL.size(), "黑市池 24");
        assertTrue(BlackMarket.POOL.contains("hongmeng"), "黑市应含鸿蒙灵液");
        assertTrue(BlackMarket.POOL.contains("phoenix"), "黑市应含凤凰之血");
    }

    @Test
    void starAndTreatCounters() {
        PlayerState s = NewState.create();
        s.stats.starsDone = 42;
        s.stats.petTreats = 77;
        assertEquals(0, DailyQuests.counterOf(s, "star").compareTo(new BigDecimal("42")), "star 计数 = starsDone");
        assertEquals(0, DailyQuests.counterOf(s, "treat").compareTo(new BigDecimal("77")), "treat 计数 = petTreats");
        assertTrue(WeeklyQuests.templateOf("star") != null, "周常应有升星模板");
        assertTrue(MonthlyQuests.templateOf("treat") != null, "月常应有零食模板");
    }

    @Test
    void milestoneSeventhTier() {
        assertEquals(7, GameService.ACH_MILESTONE_TIERS.length, "里程碑七档");
        assertEquals(105, GameService.ACH_MILESTONE_TIERS[6]);
        assertEquals(3_000_000, GameService.ACH_MILESTONE_BONUS[6], "105 档奖金 300 万");
    }

    @Test
    void newAchievementsEvaluate() {
        PlayerState s = NewState.create();
        s.stats.starsDone = 100;
        s.stats.petTreats = 200;
        var got = Achievements.evaluate(s);
        assertTrue(got.contains("star_100"), "升星 100 应解锁");
        assertTrue(got.contains("treat_200"), "零食 200 应解锁");
        assertEquals(113, Achievements.ALL.size(), "成就 113");
    }
}
