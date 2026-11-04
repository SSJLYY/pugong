package com.gongpu.game;

import com.gongpu.cfg.GameData;
import com.gongpu.common.Num;
import com.gongpu.state.NewState;
import com.gongpu.state.PlayerState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第五批第 6 轮：成就里程碑扩充（24 → 28）——终身击杀/百万金币/Boss 次数/开山一击。
 */
class AchievementMilestoneTest {

    @BeforeAll
    static void initGameData() {
        new GameData(null, null, null, null, null, null, null);
    }

    @Test
    void totalIs28() {
        // 28 = 第五批口径；第六批 +6；第七批 +2（套装）+2（淬炼/洗炼）+2（深渊）
        assertEquals(52, Achievements.ALL.size());
    }

    @Test
    void newMilestonesEvaluate() {
        PlayerState s = NewState.create();
        s.stats.lifetimeKills = Num.of(10_000);
        List<String> got = Achievements.evaluate(s);
        assertTrue(got.contains("kill_10k"), "终身击杀 1 万应点亮万怪斩：" + got);
        s.gold = Num.of(1_000_000);
        assertTrue(Achievements.evaluate(s).contains("gold_1m"), "百万金币应点亮富甲一方");
        s.stats.bossKillCount = 10;
        assertTrue(Achievements.evaluate(s).contains("boss_10"), "Boss 10 杀应点亮屠龙者");
        s.stats.bestHit = new BigDecimal("1000000");
        assertTrue(Achievements.evaluate(s).contains("best_hit_1m"), "单拳百万应点亮开山一击");
    }

    @Test
    void belowThresholdStaysLocked() {
        PlayerState s = NewState.create();
        s.stats.lifetimeKills = Num.of(9_999);
        s.stats.bossKillCount = 9;
        s.stats.bestHit = new BigDecimal("999999");
        List<String> got = Achievements.evaluate(s);
        assertTrue(!got.contains("kill_10k") && !got.contains("boss_10") && !got.contains("best_hit_1m"),
                "差一点不应点亮：" + got);
    }
}
